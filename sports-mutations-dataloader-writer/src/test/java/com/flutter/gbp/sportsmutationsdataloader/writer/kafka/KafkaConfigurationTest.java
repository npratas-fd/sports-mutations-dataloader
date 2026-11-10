package com.flutter.gbp.sportsmutationsdataloader.writer.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException;
import com.datastax.oss.driver.api.core.servererrors.InvalidConfigurationInQueryException;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.servererrors.SyntaxError;
import com.datastax.oss.driver.api.core.servererrors.UnauthorizedException;
import com.datastax.oss.driver.api.core.servererrors.UnavailableException;
import com.datastax.oss.driver.api.core.servererrors.WriteTimeoutException;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.servererrors.WriteType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.DefaultErrorHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Pins the "retry Cassandra failures forever" ruling: the backoff never stops, and only availability-type driver
 * failures get it. Query validation failures ({@code QueryValidationException}) are permanent: skipped and counted.
 */
class KafkaConfigurationTest {

    @Test
    void cassandraBackOffNeverStopsAndCapsAtThirtySeconds() {
        BackOffExecution execution = KafkaConfiguration.cassandraBackOff().start();

        List<Long> intervals = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            intervals.add(execution.nextBackOff());
        }

        assertThat(intervals).doesNotContain(BackOffExecution.STOP);
        assertThat(intervals.get(0)).isEqualTo(500L);
        assertThat(intervals).allMatch(interval -> interval >= 500L && interval <= 30_000L);
        assertThat(intervals.subList(100, intervals.size())).containsOnly(30_000L);
    }

    @Test
    void availabilityDriverFailuresAreRetriedWhenWrapped() {
        DriverTimeoutException timeout = new DriverTimeoutException("Query timed out after PT2S");

        assertThat(KafkaConfiguration.isRetriableCassandraFailure(timeout)).isTrue();
        assertThat(KafkaConfiguration.isRetriableCassandraFailure(
                new ListenerExecutionFailedException("Listener failed", timeout))).isTrue();
        assertThat(KafkaConfiguration.isRetriableCassandraFailure(new CompletionException(timeout))).isTrue();
        assertThat(KafkaConfiguration.isRetriableCassandraFailure(
                new UnavailableException(null, ConsistencyLevel.LOCAL_QUORUM, 2, 1))).isTrue();
        assertThat(KafkaConfiguration.isRetriableCassandraFailure(
                new WriteTimeoutException(null, ConsistencyLevel.LOCAL_QUORUM, 1, 2, WriteType.BATCH))).isTrue();
        assertThat(KafkaConfiguration.isRetriableCassandraFailure(AllNodesFailedException.fromErrors(Map.of())))
                .isTrue();
        assertThat(KafkaConfiguration.isPermanentCassandraFailure(timeout)).isFalse();
    }

    @Test
    void queryValidationFailuresArePermanentNotRetried() {
        List<Exception> permanent = List.of(
                new InvalidQueryException(null, "Undefined column name bet"),
                new SyntaxError(null, "line 1:0 no viable alternative"),
                new UnauthorizedException(null, "User has no MODIFY permission"),
                new InvalidConfigurationInQueryException(null, "Unknown keyspace"),
                new AlreadyExistsException(null, "sports_mutations_dataloader", "bets"));

        for (Exception failure : permanent) {
            Exception wrapped = new ListenerExecutionFailedException("Listener failed", failure);
            assertThat(KafkaConfiguration.isPermanentCassandraFailure(failure)).as(failure.toString()).isTrue();
            assertThat(KafkaConfiguration.isPermanentCassandraFailure(wrapped)).as(failure.toString()).isTrue();
            assertThat(KafkaConfiguration.isRetriableCassandraFailure(failure)).as(failure.toString()).isFalse();
            assertThat(KafkaConfiguration.isRetriableCassandraFailure(wrapped)).as(failure.toString()).isFalse();
        }
    }

    @Test
    void otherFailuresAreNotCassandraFailures() {
        for (Exception failure : List.of(new NullPointerException(), new IllegalArgumentException("betId must not be empty"),
                new ListenerExecutionFailedException("Listener failed", new IllegalArgumentException()))) {
            assertThat(KafkaConfiguration.isRetriableCassandraFailure(failure)).isFalse();
            assertThat(KafkaConfiguration.isPermanentCassandraFailure(failure)).isFalse();
        }
    }

    @Test
    void errorHandlerSkipsPermanentFailuresAndCountsThem() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultErrorHandler errorHandler = new KafkaConfiguration().kafkaErrorHandler(registry);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("sb_bet_processor_topic", 3, 42L, "key", "value");
        InvalidQueryException invalid = new InvalidQueryException(null, "Undefined column name bet");

        boolean recovered = errorHandler.handleOne(
                new ListenerExecutionFailedException("Listener failed", invalid), record, null, null);

        assertThat(recovered).isTrue();
        assertThat(registry.get("sports.mutations.dataloader.writer.cassandra.permanent.failures")
                .tag("topic", "sb_bet_processor_topic").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("sports.mutations.dataloader.writer.cassandra.retries").counter()).isNull();
        assertThat(registry.find("sports.mutations.dataloader.writer.records.skipped").counter()).isNull();
    }

    @Test
    void errorHandlerSkipsOtherFailuresAsSkippedRecords() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultErrorHandler errorHandler = new KafkaConfiguration().kafkaErrorHandler(registry);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("sb_bet_processor_topic", 3, 42L, "key", "value");

        boolean recovered = errorHandler.handleOne(
                new ListenerExecutionFailedException("Listener failed", new IllegalStateException("bad")), record,
                null, null);

        assertThat(recovered).isTrue();
        assertThat(registry.get("sports.mutations.dataloader.writer.records.skipped").tag("topic", "sb_bet_processor_topic")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("sports.mutations.dataloader.writer.cassandra.permanent.failures").counter()).isNull();
    }
}
