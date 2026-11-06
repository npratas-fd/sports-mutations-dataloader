package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher;

import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.ppb.platform.sb.fbp.proto.bet.changeinterms.ChangeInTermsOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AccaFreezeEnricherTest {

    private AccaFreezeEnricher accaFreezeEnricher;

    @BeforeMethod
    public void setUp() {
        accaFreezeEnricher = new AccaFreezeEnricher();
    }

    @Test
    void testNullBet() {
        accaFreezeEnricher.enrich(null, List.of(ChangeInTermsOuterClass.ChangeInTerms.newBuilder()
                .setReasonCode(ChangeInTermsOuterClass.ChangeInTerms.Reason.LEG_FREEZE).build()
        ));
    }

    @Test
    void testBetWithEmptyLegs() {
        accaFreezeEnricher.enrich(Bet.newBuilder(), List.of(ChangeInTermsOuterClass.ChangeInTerms.newBuilder()
                .setReasonCode(ChangeInTermsOuterClass.ChangeInTerms.Reason.LEG_FREEZE).build()
        ));
    }

    @Test
    void testNullChangeInTermsList() {
        accaFreezeEnricher.enrich(Bet.newBuilder().addLegs(Leg.newBuilder().build()), null);
    }

    @Test
    void testEmptyChangeInTermsList() {
        accaFreezeEnricher.enrich(Bet.newBuilder().addLegs(Leg.newBuilder().build()), Collections.emptyList());
    }

    @Test
    void testWithNoAccaFrozenLegs() {
        Bet.Builder betBuilder = createBetBuilder();

        ChangeInTermsOuterClass.ChangeInTerms changeInTerms = ChangeInTermsOuterClass.ChangeInTerms.newBuilder()
                .setReasonCode(ChangeInTermsOuterClass.ChangeInTerms.Reason.BOOST)
                .build();

        accaFreezeEnricher.enrich(betBuilder, List.of(changeInTerms));

        for (Leg leg : betBuilder.getLegsList()) {
            assertFalse(leg.getLegQuotingData().getIsAccaFrozen());
        }
    }

    @Test
    void testWithAccaFrozenLegs() {
        Bet.Builder betBuilder = createBetBuilder();

        ChangeInTermsOuterClass.ChangeInTerms changeInTerms = ChangeInTermsOuterClass.ChangeInTerms.newBuilder()
                .setReasonCode(ChangeInTermsOuterClass.ChangeInTerms.Reason.LEG_FREEZE)
                .addChangeLegs(ChangeInTermsOuterClass.ChangeLeg.newBuilder().setLegNo(1).build())
                .build();

        accaFreezeEnricher.enrich(betBuilder, List.of(changeInTerms));

        for (Leg leg : betBuilder.getLegsList()) {
            if (leg.getLegQuotingData().getLegNumber() == 1) {
                assertTrue(leg.getLegQuotingData().getIsAccaFrozen());
            } else {
                assertFalse(leg.getLegQuotingData().getIsAccaFrozen());
            }
        }
    }

    private Bet.Builder createBetBuilder() {
        Bet.Builder betBuilder = Bet.newBuilder();

        Leg leg1 = Leg.newBuilder()
                .setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(1).build())
                .build();

        Leg leg2 = Leg.newBuilder()
                .setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(2).build())
                .build();

        betBuilder.addLegs(leg1);
        betBuilder.addLegs(leg2);

        return betBuilder;
    }
}