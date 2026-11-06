package com.flutter.gbp.betstore.writer.event;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.betstore.cassandra.BetRecord;
import com.flutter.gbp.betstore.cassandra.BetWriteRepository;
import com.flutter.gbp.fcq.bet.Bet;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the FCQ bet event rules ({@code FbpEventProcessor} / {@code AbpEventProcessor}) to a {@link SourceEvent}
 * and writes the result to Cassandra.
 *
 * <ol>
 *     <li>virtual bets are skipped;</li>
 *     <li>FBP bets with an OBP leg are skipped unless OBP processing is enabled (ABP ignores the toggle);</li>
 *     <li>the source creation latency is recorded;</li>
 *     <li>a non-numeric account id is skipped;</li>
 *     <li>a settled bet is deleted, ordered by the write timestamp when the event has one;</li>
 *     <li>otherwise the bet is converted and upserted. The write timestamp comes from {@link WriteTimestampResolver};
 *     {@code bet_stream_timestamp} is always {@code CREATION_TIME}. Both are required.</li>
 * </ol>
 *
 * <p>Invalid events and conversion errors are logged, counted and skipped. Cassandra failures ({@code DriverException})
 * propagate so the caller does not commit the offset.
 */
public class BetEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetEventHandler.class);

    static final String EVENTS_METRIC = "betstore.writer.events";
    static final String LATENCY_METRIC = "betstore.writer.source.creation.latency";

    /**
     * Result of handling one event.
     */
    public enum Outcome {
        UPSERTED,
        DELETED,
        SKIPPED_VIRTUAL,
        SKIPPED_OBP,
        SKIPPED_INVALID_ACCOUNT,
        SKIPPED_NO_TIMESTAMP,
        SKIPPED_CONVERSION_ERROR
    }

    private final BetWriteRepository repository;
    private final WriteTimestampResolver timestampResolver;
    private final boolean obpBetsProcessing;
    private final MeterRegistry meterRegistry;

    /**
     * @param repository        Cassandra write repository
     * @param timestampResolver resolves the write timestamp from the event context
     * @param obpBetsProcessing store FBP bets with OBP legs (FCQ {@code fcq.featureToggle.enable.fbp.obp.bets.processing})
     * @param meterRegistry     registry for the writer metrics
     */
    public BetEventHandler(BetWriteRepository repository, WriteTimestampResolver timestampResolver,
                           boolean obpBetsProcessing, MeterRegistry meterRegistry) {
        checkArgument(repository != null, "repository must be provided");
        checkArgument(timestampResolver != null, "timestampResolver must be provided");
        checkArgument(meterRegistry != null, "meterRegistry must be provided");
        this.repository = repository;
        this.timestampResolver = timestampResolver;
        this.obpBetsProcessing = obpBetsProcessing;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param event the event to handle
     * @return what was done with the event
     * @throws com.datastax.oss.driver.api.core.DriverException if the Cassandra write fails
     */
    public Outcome handle(SourceEvent event) {
        Outcome outcome = process(event);
        Counter.builder(EVENTS_METRIC)
                .tag("source", event.source())
                .tag("outcome", outcome.name())
                .register(meterRegistry)
                .increment();
        return outcome;
    }

    private Outcome process(SourceEvent event) {
        if (event.virtual()) {
            LOGGER.debug("Skipped processing of the virtual bet, source: {}, betId: {}", event.source(), event.betId());
            return Outcome.SKIPPED_VIRTUAL;
        }
        if (SourceEvent.FBP.equals(event.source()) && event.hasObpLeg() && !obpBetsProcessing) {
            LOGGER.debug("Skipped processing of OBP bet, source: {}, betId: {}", event.source(), event.betId());
            return Outcome.SKIPPED_OBP;
        }

        recordCreationLatency(event);

        long accountId;
        try {
            accountId = Long.parseLong(event.accountIdRaw());
        } catch (NumberFormatException e) {
            LOGGER.error("Error when parsing the account id of the bet, source: {}, betId: {}, accountId: {}",
                    event.source(), event.betId(), event.accountIdRaw());
            return Outcome.SKIPPED_INVALID_ACCOUNT;
        }

        OptionalLong writeTimestampMicros = timestampResolver.resolveMicros(event.context());
        if (event.settled()) {
            LOGGER.debug("Deleting the bet, source: {}, betId: {}, accountId: {}, writeTimestampMicros: {}",
                    event.source(), event.betId(), accountId, writeTimestampMicros);
            repository.delete(accountId, event.betId(), writeTimestampMicros);
            return Outcome.DELETED;
        }

        OptionalLong creationTimeMillis =
                WriteTimestampResolver.readMillis(event.context(), WriteTimestampResolver.CREATION_TIME);
        if (writeTimestampMicros.isEmpty() || creationTimeMillis.isEmpty()) {
            LOGGER.warn("Skipped the bet without timestamp, source: {}, betId: {}, accountId: {}, context: {}",
                    event.source(), event.betId(), accountId, event.context());
            return Outcome.SKIPPED_NO_TIMESTAMP;
        }

        Bet bet;
        try {
            bet = event.converter().get();
        } catch (RuntimeException e) {
            LOGGER.error("Error when converting the bet, source: {}, betId: {}, accountId: {}",
                    event.source(), event.betId(), accountId, e);
            return Outcome.SKIPPED_CONVERSION_ERROR;
        }

        LOGGER.debug("Persisting the bet, source: {}, betId: {}, accountId: {}", event.source(), event.betId(), accountId);
        repository.upsert(new BetRecord(accountId, event.betId(), Instant.ofEpochMilli(creationTimeMillis.getAsLong()),
                bet, writeTimestampMicros.getAsLong()));
        return Outcome.UPSERTED;
    }

    private void recordCreationLatency(SourceEvent event) {
        OptionalLong creationTimeMillis =
                WriteTimestampResolver.readMillis(event.context(), WriteTimestampResolver.CREATION_TIME);
        if (creationTimeMillis.isEmpty()) {
            return;
        }
        Timer.builder(LATENCY_METRIC)
                .tag("source", event.source())
                .tag("sourceTag", event.sourceTag())
                .register(meterRegistry)
                .record(Duration.ofMillis(System.currentTimeMillis() - creationTimeMillis.getAsLong()));
    }
}
