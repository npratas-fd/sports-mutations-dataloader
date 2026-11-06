package com.flutter.gbp.betstore.writer.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.DriverTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Pins the "retry Cassandra failures forever" ruling: the backoff never stops, and only driver failures get it.
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
    void driverFailureIsCassandraFailureWhenWrapped() {
        DriverTimeoutException timeout = new DriverTimeoutException("Query timed out after PT2S");

        assertThat(KafkaConfiguration.isCassandraFailure(timeout)).isTrue();
        assertThat(KafkaConfiguration.isCassandraFailure(
                new ListenerExecutionFailedException("Listener failed", timeout))).isTrue();
        assertThat(KafkaConfiguration.isCassandraFailure(new CompletionException(timeout))).isTrue();
    }

    @Test
    void otherFailuresAreNotCassandraFailures() {
        assertThat(KafkaConfiguration.isCassandraFailure(new NullPointerException())).isFalse();
        assertThat(KafkaConfiguration.isCassandraFailure(new IllegalArgumentException("betId must not be empty")))
                .isFalse();
        assertThat(KafkaConfiguration.isCassandraFailure(
                new ListenerExecutionFailedException("Listener failed", new IllegalArgumentException()))).isFalse();
    }
}
