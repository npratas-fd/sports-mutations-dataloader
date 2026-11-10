package com.flutter.gbp.sportsmutationsdataloader.writer.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.bet.BetConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.ProductOuterClass.Product;
import com.ppb.platform.sb.fbp.proto.bet.SourceTagOuterClass.SourceTag;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass.Leg;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Entity;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.SelectionInfo;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass.BetContext;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass.BetDomainEvent;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FbpEventAdapterTest {

    private BetConverter betConverter;
    private FbpEventAdapter adapter;

    @BeforeEach
    void setUp() {
        betConverter = mock(BetConverter.class);
        adapter = new FbpEventAdapter(betConverter);
    }

    @Test
    void eventWithoutBetIsEmpty() {
        assertThat(adapter.adapt(BetDomainEvent.getDefaultInstance())).isEmpty();
        assertThat(adapter.adapt(null)).isEmpty();
    }

    @Test
    void mapsBetFieldsAndContext() {
        BetDomainEvent event = event(bet().setIsSettled(true).setSourceTag(SourceTag.GBP));

        SourceEvent sourceEvent = adapter.adapt(event).orElseThrow();

        assertThat(sourceEvent.source()).isEqualTo(SourceEvent.FBP);
        assertThat(sourceEvent.accountIdRaw()).isEqualTo("12345");
        assertThat(sourceEvent.betId()).isEqualTo("O/123456789/0001");
        assertThat(sourceEvent.settled()).isTrue();
        assertThat(sourceEvent.virtual()).isFalse();
        assertThat(sourceEvent.hasObpLeg()).isFalse();
        assertThat(sourceEvent.context()).isEqualTo(Map.of("CREATION_TIME", "1785343197501"));
        assertThat(sourceEvent.sourceTag()).isEqualTo("gbp");
    }

    @Test
    void unrecognizedSourceTagIsUnspecified() {
        BetDomainEvent event = event(bet().setSourceTagValue(99));

        assertThat(adapter.adapt(event).orElseThrow().sourceTag()).isEqualTo("unspecified");
    }

    @Test
    void virtualSportsIsVirtual() {
        BetDomainEvent event = event(bet().setProduct(Product.VIRTUAL_SPORTS));

        assertThat(adapter.adapt(event).orElseThrow().virtual()).isTrue();
    }

    @Test
    void outcomeBasedLegIsObp() {
        BetDomainEvent event = event(bet()
                .addLegs(Leg.newBuilder().setLegType(Leg.LegType.SIMPLE_SELECTION))
                .addLegs(Leg.newBuilder().setLegType(Leg.LegType.OUTCOME_BASED)));

        assertThat(adapter.adapt(event).orElseThrow().hasObpLeg()).isTrue();
    }

    @Test
    void partWithMiscIdIsObp() {
        BetDomainEvent event = event(bet().addLegs(Leg.newBuilder()
                .setLegType(Leg.LegType.SIMPLE_SELECTION)
                .addPart(part(Map.of("RAMP", "1")))
                .addPart(part(Map.of("MISC", "123")))));

        assertThat(adapter.adapt(event).orElseThrow().hasObpLeg()).isTrue();
    }

    @Test
    void marketBasedLegWithoutMiscIdIsNotObp() {
        BetDomainEvent event = event(bet().addLegs(Leg.newBuilder()
                .setLegType(Leg.LegType.SIMPLE_SELECTION)
                .addPart(part(Map.of("RAMP", "1")))));

        assertThat(adapter.adapt(event).orElseThrow().hasObpLeg()).isFalse();
    }

    @Test
    void converterIsCalledLazilyWithTheEvent() {
        BetDomainEvent event = event(bet());
        Bet converted = Bet.newBuilder().setBetReceiptId("O/123456789/0001").build();
        when(betConverter.convert(event)).thenReturn(converted);

        SourceEvent sourceEvent = adapter.adapt(event).orElseThrow();
        verifyNoInteractions(betConverter);

        assertThat(sourceEvent.converter().get()).isSameAs(converted);
        verify(betConverter).convert(event);
    }

    private static BetOuterClass.Bet.Builder bet() {
        return BetOuterClass.Bet.newBuilder()
                .setAccountId("12345")
                .setBetId("O/123456789/0001")
                .setProduct(Product.SPORTSBOOK);
    }

    private static BetDomainEvent event(BetOuterClass.Bet.Builder bet) {
        return BetDomainEvent.newBuilder()
                .setContext(BetContext.newBuilder().putContext("CREATION_TIME", "1785343197501"))
                .setBet(bet)
                .build();
    }

    private static Part part(Map<String, String> selectionIds) {
        return Part.newBuilder()
                .setSelectionInfo(SelectionInfo.newBuilder().setEntity(Entity.newBuilder().putAllIds(selectionIds)))
                .build();
    }
}
