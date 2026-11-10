package com.flutter.gbp.sportsmutationsdataloader.writer.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flutter.gbp.sportsmutationsdataloader.writer.event.AbpEventAdapter;
import com.flutter.gbp.sportsmutationsdataloader.writer.event.BetEventHandler;
import com.flutter.gbp.sportsmutationsdataloader.writer.event.FbpEventAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BetEventListenersTest {

    private BetEventHandler handler;
    private SimpleMeterRegistry meterRegistry;
    private BetEventListeners listeners;

    @BeforeEach
    void setUp() {
        handler = mock(BetEventHandler.class);
        meterRegistry = new SimpleMeterRegistry();
        listeners = new BetEventListeners(
                new FbpEventAdapter(mock(com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.bet.BetConverter.class)),
                new AbpEventAdapter(mock(com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet.BetConverter.class)),
                handler, meterRegistry);
    }

    @Test
    void fbpEventWithoutBetIsCountedAndSkipped() {
        // An empty payload is deserialized to null by FbpDeserializer (parseDelimitedFrom at EOF)
        listeners.onFbpEvent(new ConsumerRecord<>("fbp", 0, 7L, "key", null));
        listeners.onFbpEvent(new ConsumerRecord<>("fbp", 0, 8L, "key",
                com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass.BetDomainEvent.getDefaultInstance()));

        assertThat(skippedNoBet("fbp")).isEqualTo(2.0);
        verifyNoInteractions(handler);
    }

    @Test
    void abpEventWithoutBetIsCountedAndSkipped() {
        listeners.onAbpEvent(new ConsumerRecord<>("abp", 0, 7L, "key", null));

        assertThat(skippedNoBet("abp")).isEqualTo(1.0);
        assertThat(skippedNoBet("fbp")).isZero();
        verifyNoInteractions(handler);
    }

    private double skippedNoBet(String source) {
        var counter = meterRegistry.find(BetEventHandler.EVENTS_METRIC)
                .tags("source", source, "sourceTag", "unspecified", "outcome", BetEventListeners.SKIPPED_NO_BET)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
