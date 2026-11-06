package com.flutter.gbp.betstore.writer.kafka;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.betstore.writer.event.AbpEventAdapter;
import com.flutter.gbp.betstore.writer.event.BetEventHandler;
import com.flutter.gbp.betstore.writer.event.FbpEventAdapter;
import com.flutter.gbp.betstore.writer.event.SourceEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * One listener per bet topic. Each record is adapted to a {@link SourceEvent} and handed to {@link BetEventHandler}.
 *
 * <p>The listener returns only after the Cassandra write, and the container commits the offset of each record after
 * the listener returns ({@code ack-mode=record}). A Cassandra failure propagates to the error handler of
 * {@link KafkaConfiguration}, which retries the record without committing it.
 *
 * <p>An event without a bet (including an empty payload, which the deserializer turns into {@code null}) is logged,
 * counted as {@code betstore.writer.events{source, outcome=SKIPPED_NO_BET}} and skipped (its offset is committed).
 *
 * <p>Each listener starts only when {@code bet-store.consumers.<fbp|abp>.enabled} is {@code true} (the default).
 * {@code idIsGroup = false}: both listeners use {@code spring.kafka.consumer.group-id}.
 */
public class BetEventListeners {

    /** Listener container id of the FBP listener. */
    public static final String FBP_LISTENER_ID = "fbpBetEvents";
    /** Listener container id of the ABP listener. */
    public static final String ABP_LISTENER_ID = "abpBetEvents";
    /** {@code outcome} tag of {@link BetEventHandler#EVENTS_METRIC} for an event without a bet. */
    public static final String SKIPPED_NO_BET = "SKIPPED_NO_BET";

    private static final Logger LOGGER = LoggerFactory.getLogger(BetEventListeners.class);

    private final FbpEventAdapter fbpEventAdapter;
    private final AbpEventAdapter abpEventAdapter;
    private final BetEventHandler betEventHandler;
    private final MeterRegistry meterRegistry;

    /**
     * @param fbpEventAdapter adapter of FBP events
     * @param abpEventAdapter adapter of ABP events
     * @param betEventHandler applies the event rules and writes to Cassandra
     * @param meterRegistry   registry of the skipped-event counter
     */
    public BetEventListeners(FbpEventAdapter fbpEventAdapter, AbpEventAdapter abpEventAdapter,
                             BetEventHandler betEventHandler, MeterRegistry meterRegistry) {
        checkArgument(fbpEventAdapter != null, "fbpEventAdapter must be provided");
        checkArgument(abpEventAdapter != null, "abpEventAdapter must be provided");
        checkArgument(betEventHandler != null, "betEventHandler must be provided");
        checkArgument(meterRegistry != null, "meterRegistry must be provided");
        this.fbpEventAdapter = fbpEventAdapter;
        this.abpEventAdapter = abpEventAdapter;
        this.betEventHandler = betEventHandler;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param record FBP (online) bet event
     */
    @KafkaListener(id = FBP_LISTENER_ID, idIsGroup = false,
            topics = "${bet-store.consumers.fbp.topic:sb_bet_processor_topic}",
            autoStartup = "${bet-store.consumers.fbp.enabled:true}",
            containerFactory = KafkaConfiguration.FBP_CONTAINER_FACTORY)
    public void onFbpEvent(
            ConsumerRecord<String, com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass.BetDomainEvent> record) {
        handle(record, SourceEvent.FBP, fbpEventAdapter.adapt(record.value()));
    }

    /**
     * @param record ABP (retail) bet event
     */
    @KafkaListener(id = ABP_LISTENER_ID, idIsGroup = false,
            topics = "${bet-store.consumers.abp.topic:retail_bet_processor_topic}",
            autoStartup = "${bet-store.consumers.abp.enabled:true}",
            containerFactory = KafkaConfiguration.ABP_CONTAINER_FACTORY)
    public void onAbpEvent(
            ConsumerRecord<String, com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass.BetDomainEvent> record) {
        handle(record, SourceEvent.ABP, abpEventAdapter.adapt(record.value()));
    }

    private void handle(ConsumerRecord<String, ?> record, String source, Optional<SourceEvent> event) {
        if (event.isEmpty()) {
            LOGGER.warn("Skipped event without bet, topic: {}, partition: {}, offset: {}, key: {}",
                    record.topic(), record.partition(), record.offset(), record.key());
            Counter.builder(BetEventHandler.EVENTS_METRIC)
                    .tag("source", source)
                    .tag("outcome", SKIPPED_NO_BET)
                    .register(meterRegistry)
                    .increment();
            return;
        }
        BetEventHandler.Outcome outcome = betEventHandler.handle(event.get());
        LOGGER.debug("Handled event, topic: {}, partition: {}, offset: {}, betId: {}, outcome: {}",
                record.topic(), record.partition(), record.offset(), event.get().betId(), outcome);
    }
}
