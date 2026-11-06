package com.flutter.gbp.betstore.writer.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.flutter.gbp.betstore.cassandra.BetRecord;
import com.flutter.gbp.betstore.cassandra.BetWriteRepository;
import com.flutter.gbp.betstore.writer.config.WriterProperties.TimestampField;
import com.flutter.gbp.betstore.writer.event.BetEventHandler.Outcome;
import com.flutter.gbp.fcq.bet.Bet;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BetEventHandlerTest {

    private static final long ACCOUNT_ID = 12345L;
    private static final String BET_ID = "O/123456789/0001";
    private static final long CREATION_TIME = 1785343197501L;
    private static final long GENERATION_CLOCK = 1785343197635L;
    private static final Map<String, String> CONTEXT = Map.of(
            "CREATION_TIME", Long.toString(CREATION_TIME),
            "generationClock", Long.toString(GENERATION_CLOCK));
    private static final Bet BET = Bet.newBuilder().setBetReceiptId(BET_ID).build();

    private BetWriteRepository repository;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        repository = mock(BetWriteRepository.class);
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void virtualIsSkipped() {
        assertThat(handler(true).handle(fbpEvent().virtual(true).build())).isEqualTo(Outcome.SKIPPED_VIRTUAL);
        assertThat(handler(true).handle(abpEvent().virtual(true).settled(true).build()))
                .isEqualTo(Outcome.SKIPPED_VIRTUAL);
        verifyNoInteractions(repository);
    }

    @Test
    void obpSkippedWhenToggleOffForFbp() {
        assertThat(handler(false).handle(fbpEvent().hasObpLeg(true).build())).isEqualTo(Outcome.SKIPPED_OBP);
        assertThat(handler(false).handle(fbpEvent().hasObpLeg(true).settled(true).build()))
                .isEqualTo(Outcome.SKIPPED_OBP);
        verifyNoInteractions(repository);
    }

    @Test
    void obpProcessedWhenToggleOn() {
        SourceEvent event = fbpEvent().hasObpLeg(true).build();

        assertThat(handler(true).handle(event)).isEqualTo(Outcome.UPSERTED);
        verify(repository).upsert(any(BetRecord.class));
    }

    @Test
    void obpToggleIgnoredForAbp() {
        SourceEvent event = abpEvent().hasObpLeg(true).build();

        assertThat(handler(false).handle(event)).isEqualTo(Outcome.UPSERTED);
        verify(repository).upsert(any(BetRecord.class));
    }

    @Test
    void nonObpFbpBetProcessedWhenToggleOff() {
        assertThat(handler(false).handle(fbpEvent().build())).isEqualTo(Outcome.UPSERTED);
        verify(repository).upsert(any(BetRecord.class));
    }

    @Test
    void nonNumericAccountIsSkipped() {
        BetEventHandler handler = handler(true);

        assertThat(handler.handle(fbpEvent().accountIdRaw("abc").build())).isEqualTo(Outcome.SKIPPED_INVALID_ACCOUNT);
        assertThat(handler.handle(fbpEvent().accountIdRaw("").build())).isEqualTo(Outcome.SKIPPED_INVALID_ACCOUNT);
        assertThat(handler.handle(fbpEvent().accountIdRaw("abc").settled(true).build()))
                .isEqualTo(Outcome.SKIPPED_INVALID_ACCOUNT);
        verifyNoInteractions(repository);
    }

    @Test
    void openBetIsUpsertedWithResolvedTimestampAndCreationTimeColumn() {
        Outcome outcome = handler(TimestampField.GENERATION_CLOCK).handle(fbpEvent().build());

        assertThat(outcome).isEqualTo(Outcome.UPSERTED);
        verify(repository).upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(CREATION_TIME), BET,
                GENERATION_CLOCK * 1000));
    }

    @Test
    void openBetUsesCreationTimeByDefault() {
        handler(true).handle(abpEvent().build());

        verify(repository).upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(CREATION_TIME), BET,
                CREATION_TIME * 1000));
    }

    @Test
    void upsertWithoutTimestampIsSkipped() {
        Outcome noContext = handler(true).handle(fbpEvent().context(Map.of()).build());
        Outcome noGenerationClock = handler(TimestampField.GENERATION_CLOCK)
                .handle(fbpEvent().context(Map.of("CREATION_TIME", Long.toString(CREATION_TIME))).build());
        // The bet_stream_timestamp column is always CREATION_TIME, so it is required even when another field orders writes
        Outcome noCreationTime = handler(TimestampField.GENERATION_CLOCK)
                .handle(fbpEvent().context(Map.of("generationClock", Long.toString(GENERATION_CLOCK))).build());

        assertThat(noContext).isEqualTo(Outcome.SKIPPED_NO_TIMESTAMP);
        assertThat(noGenerationClock).isEqualTo(Outcome.SKIPPED_NO_TIMESTAMP);
        assertThat(noCreationTime).isEqualTo(Outcome.SKIPPED_NO_TIMESTAMP);
        verifyNoInteractions(repository);
    }

    @Test
    void settledIsDeletedWithTimestamp() {
        Outcome outcome = handler(true).handle(fbpEvent().settled(true).build());

        assertThat(outcome).isEqualTo(Outcome.DELETED);
        verify(repository).delete(ACCOUNT_ID, BET_ID, OptionalLong.of(CREATION_TIME * 1000));
    }

    @Test
    void settledWithoutTimestampIsDeletedAnyway() {
        Outcome outcome = handler(true).handle(abpEvent().settled(true).context(Map.of()).build());

        assertThat(outcome).isEqualTo(Outcome.DELETED);
        verify(repository).delete(ACCOUNT_ID, BET_ID, OptionalLong.empty());
    }

    @Test
    void settledBetIsNotConverted() {
        Supplier<Bet> failing = () -> {
            throw new IllegalStateException("must not convert a settled bet");
        };

        assertThat(handler(true).handle(fbpEvent().settled(true).converter(failing).build())).isEqualTo(Outcome.DELETED);
    }

    @Test
    void conversionErrorIsSkipped() {
        Supplier<Bet> failing = () -> {
            throw new IllegalArgumentException("bad bet");
        };

        assertThat(handler(true).handle(fbpEvent().converter(failing).build()))
                .isEqualTo(Outcome.SKIPPED_CONVERSION_ERROR);
        verifyNoInteractions(repository);
    }

    @Test
    void repositoryFailurePropagates() {
        DriverTimeoutException timeout = new DriverTimeoutException("timeout");
        doThrow(timeout).when(repository).upsert(any(BetRecord.class));
        doThrow(timeout).when(repository).delete(ACCOUNT_ID, BET_ID, OptionalLong.of(CREATION_TIME * 1000));
        BetEventHandler handler = handler(true);

        assertThatThrownBy(() -> handler.handle(fbpEvent().build())).isSameAs(timeout);
        assertThatThrownBy(() -> handler.handle(fbpEvent().settled(true).build())).isSameAs(timeout);
    }

    @Test
    void outcomesAreCountedPerSource() {
        BetEventHandler handler = handler(false);

        handler.handle(fbpEvent().build());
        handler.handle(fbpEvent().hasObpLeg(true).build());
        handler.handle(abpEvent().settled(true).build());

        assertThat(eventCount("fbp", "gbp", Outcome.UPSERTED)).isEqualTo(1.0);
        assertThat(eventCount("fbp", "gbp", Outcome.SKIPPED_OBP)).isEqualTo(1.0);
        assertThat(eventCount("abp", "unspecified", Outcome.DELETED)).isEqualTo(1.0);
    }

    @Test
    void creationLatencyIsRecordedPerSourceAndSourceTag() {
        BetEventHandler handler = handler(true);

        handler.handle(fbpEvent().build());
        handler.handle(fbpEvent().settled(true).build());
        handler.handle(fbpEvent().virtual(true).build());

        assertThat(meterRegistry.get("betstore.writer.source.creation.latency")
                .tag("source", "fbp").tag("sourceTag", "gbp").timer().count()).isEqualTo(2);
    }

    @Test
    void cassandraWritesAreTimedPerOperation() {
        BetEventHandler handler = handler(true);

        handler.handle(fbpEvent().build());
        handler.handle(fbpEvent().build());
        handler.handle(fbpEvent().settled(true).build());
        handler.handle(fbpEvent().virtual(true).build());

        assertThat(writeTimer("upsert", "success").count()).isEqualTo(2);
        assertThat(writeTimer("delete", "success").count()).isEqualTo(1);
    }

    @Test
    void failedCassandraWritesAreTimedAsFailures() {
        doThrow(new DriverTimeoutException("timeout")).when(repository).upsert(any(BetRecord.class));
        BetEventHandler handler = handler(true);

        assertThatThrownBy(() -> handler.handle(fbpEvent().build())).isInstanceOf(DriverTimeoutException.class);

        assertThat(writeTimer("upsert", "failure").count()).isEqualTo(1);
        assertThat(meterRegistry.find("betstore.writer.cassandra.write").tag("outcome", "success").timer()).isNull();
    }

    @Test
    void unbatchedUpsertIsCountedAsBatchFallback() {
        when(repository.upsert(any(BetRecord.class)))
                .thenReturn(BetWriteRepository.WriteMode.UNBATCHED, BetWriteRepository.WriteMode.LOGGED_BATCH);
        BetEventHandler handler = handler(true);

        assertThat(handler.handle(fbpEvent().build())).isEqualTo(Outcome.UPSERTED);
        assertThat(handler.handle(fbpEvent().build())).isEqualTo(Outcome.UPSERTED);

        assertThat(meterRegistry.get("betstore.writer.cassandra.batch.fallbacks").tag("source", "fbp").counter().count())
                .isEqualTo(1.0);
        assertThat(writeTimer("upsert", "success").count()).isEqualTo(2);
    }

    @Test
    void loggedBatchUpsertIsNotCountedAsFallback() {
        when(repository.upsert(any(BetRecord.class))).thenReturn(BetWriteRepository.WriteMode.LOGGED_BATCH);

        handler(true).handle(fbpEvent().build());

        assertThat(meterRegistry.find("betstore.writer.cassandra.batch.fallbacks").counter()).isNull();
    }

    private Timer writeTimer(String operation, String outcome) {
        return meterRegistry.get("betstore.writer.cassandra.write")
                .tag("operation", operation).tag("outcome", outcome).timer();
    }

    private double eventCount(String source, String sourceTag, Outcome outcome) {
        return meterRegistry.get("betstore.writer.events")
                .tag("source", source).tag("sourceTag", sourceTag).tag("outcome", outcome.name()).counter().count();
    }

    private BetEventHandler handler(boolean obpBetsProcessing) {
        return new BetEventHandler(repository, new WriteTimestampResolver(TimestampField.CREATION_TIME),
                obpBetsProcessing, meterRegistry);
    }

    private BetEventHandler handler(TimestampField timestampField) {
        return new BetEventHandler(repository, new WriteTimestampResolver(timestampField), true, meterRegistry);
    }

    private static EventBuilder fbpEvent() {
        return new EventBuilder(SourceEvent.FBP, "gbp");
    }

    private static EventBuilder abpEvent() {
        return new EventBuilder(SourceEvent.ABP, "unspecified");
    }

    private static final class EventBuilder {
        private final String source;
        private final String sourceTag;
        private String accountIdRaw = Long.toString(ACCOUNT_ID);
        private boolean settled;
        private boolean virtual;
        private boolean hasObpLeg;
        private Map<String, String> context = CONTEXT;
        private Supplier<Bet> converter = () -> BET;

        private EventBuilder(String source, String sourceTag) {
            this.source = source;
            this.sourceTag = sourceTag;
        }

        EventBuilder accountIdRaw(String value) {
            accountIdRaw = value;
            return this;
        }

        EventBuilder settled(boolean value) {
            settled = value;
            return this;
        }

        EventBuilder virtual(boolean value) {
            virtual = value;
            return this;
        }

        EventBuilder hasObpLeg(boolean value) {
            hasObpLeg = value;
            return this;
        }

        EventBuilder context(Map<String, String> value) {
            context = value;
            return this;
        }

        EventBuilder converter(Supplier<Bet> value) {
            converter = value;
            return this;
        }

        SourceEvent build() {
            return new SourceEvent(source, accountIdRaw, BET_ID, settled, virtual, hasObpLeg, context, sourceTag,
                    converter);
        }
    }
}
