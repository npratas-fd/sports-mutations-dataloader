package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.domain.ImmutableBetContext;
import com.flutter.gbp.betcontext.domain.ImmutableBetLegContext;
import com.flutter.gbp.betcontext.domain.ImmutableLegContext;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.google.protobuf.DoubleValue;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class BetLegProbabilityEnricherTest {

    private BetLegProbabilityEnricher victim;

    @BeforeMethod
    public void setUp() {
        victim = new BetLegProbabilityEnricher();
    }

    @Test(dataProvider = "noEnrichmentScenarios")
    public void shouldNotEnrichLeg(String description, BetContext betContext, BetOuterClass.Bet source) {
        Bet.Builder target = Bet.newBuilder().addLegs(legWithNumber(1));

        victim.enrich(source, betContext, target);

        assertFalse(target.getLegs(0).hasPlacementProbability(), description);
    }

    @Test(dataProvider = "singleLegEnrichmentScenarios")
    public void shouldEnrichMatchingLeg(String description, BetContext betContext, double expectedProbability) {
        Bet.Builder target = Bet.newBuilder().addLegs(legWithNumber(1));

        victim.enrich(BetOuterClass.Bet.newBuilder().build(), betContext, target);

        assertTrue(target.getLegs(0).hasPlacementProbability(), description);
        assertEquals(target.getLegs(0).getPlacementProbability(), DoubleValue.of(expectedProbability));
    }

    @Test
    public void shouldEnrichOnlyMatchedLegInMultiLegBet() {
        Bet.Builder target = Bet.newBuilder()
                .addLegs(legWithNumber(1))
                .addLegs(legWithNumber(2))
                .addLegs(legWithNumber(3));

        victim.enrich(BetOuterClass.Bet.newBuilder().build(), contextWithProbability(2, 0.5), target);

        assertFalse(target.getLegs(0).hasPlacementProbability(), "leg 1 should not be enriched");
        assertTrue(target.getLegs(1).hasPlacementProbability(), "leg 2 should be enriched");
        assertEquals(target.getLegs(1).getPlacementProbability(), DoubleValue.of(0.5));
        assertFalse(target.getLegs(2).hasPlacementProbability(), "leg 3 should not be enriched");
    }

    @Test
    public void shouldEnrichAllMatchingLegsWhenMultipleProbabilitiesPresent() {
        BetContext betContext = ImmutableBetContext.builder()
                .withLegContext(ImmutableLegContext.builder()
                        .addBetLegContext(ImmutableBetLegContext.builder()
                                .withBetLegNumber(1).withBetLegProbability(0.3).build())
                        .addBetLegContext(ImmutableBetLegContext.builder()
                                .withBetLegNumber(2).withBetLegProbability(0.6).build())
                        .build())
                .build();
        Bet.Builder target = Bet.newBuilder()
                .addLegs(legWithNumber(1))
                .addLegs(legWithNumber(2));

        victim.enrich(BetOuterClass.Bet.newBuilder().build(), betContext, target);

        assertEquals(target.getLegs(0).getPlacementProbability(), DoubleValue.of(0.3));
        assertEquals(target.getLegs(1).getPlacementProbability(), DoubleValue.of(0.6));
    }

    @Test
    public void shouldEnrichMatchedLegsAndIgnoreUnmatchedBetLegContexts() {
        BetContext betContext = ImmutableBetContext.builder()
                .withLegContext(ImmutableLegContext.builder()
                        .addBetLegContext(ImmutableBetLegContext.builder()
                                .withBetLegNumber(1).withBetLegProbability(0.4).build())
                        .addBetLegContext(ImmutableBetLegContext.builder()
                                .withBetLegNumber(99).withBetLegProbability(0.9).build()) // no matching leg
                        .build())
                .build();
        Bet.Builder target = Bet.newBuilder().addLegs(legWithNumber(1));

        victim.enrich(BetOuterClass.Bet.newBuilder().setBetId("bet-mixed").build(), betContext, target);

        assertTrue(target.getLegs(0).hasPlacementProbability());
        assertEquals(target.getLegs(0).getPlacementProbability(), DoubleValue.of(0.4));
    }

    @DataProvider
    public Object[][] noEnrichmentScenarios() {
        return new Object[][]{
                {"absent legContext",
                        ImmutableBetContext.builder().build(),
                        BetOuterClass.Bet.newBuilder().build()},
                {"empty betLegContext list",
                        ImmutableBetContext.builder()
                                .withLegContext(ImmutableLegContext.builder().build()).build(),
                        BetOuterClass.Bet.newBuilder().build()},
                {"empty betLegProbability Optional",
                        ImmutableBetContext.builder()
                                .withLegContext(ImmutableLegContext.builder()
                                        .addBetLegContext(ImmutableBetLegContext.builder()
                                                .withBetLegNumber(1)
                                                .withBetLegProbability(Optional.empty())
                                                .build())
                                        .build())
                                .build(),
                        BetOuterClass.Bet.newBuilder().build()},
                {"null betContext",
                        null,
                        BetOuterClass.Bet.newBuilder().build()},
                {"betLegContext number has no matching leg",
                        contextWithProbability(99, 0.8),
                        BetOuterClass.Bet.newBuilder().setBetId("bet-123").build()},
        };
    }

    @DataProvider
    public Object[][] singleLegEnrichmentScenarios() {
        return new Object[][]{
                {"single leg matches", contextWithProbability(1, 0.75), 0.75},
                {"leg context with lower probability", contextWithProbability(1, 0.4), 0.4},
        };
    }

    private Leg legWithNumber(int number) {
        return Leg.newBuilder()
                .setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(number).build())
                .build();
    }

    private BetContext contextWithProbability(int legNumber, double probability) {
        return ImmutableBetContext.builder()
                .withLegContext(ImmutableLegContext.builder()
                        .addBetLegContext(ImmutableBetLegContext.builder()
                                .withBetLegNumber(legNumber)
                                .withBetLegProbability(probability)
                                .build())
                        .build())
                .build();
    }
}
