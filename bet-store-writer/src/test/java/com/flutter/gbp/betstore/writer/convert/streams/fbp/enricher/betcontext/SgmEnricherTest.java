package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.betstore.writer.convert.utils.SgmGroupPriceConverter;
import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.domain.ImmutableBetContext;
import com.flutter.gbp.betcontext.domain.ImmutablePrice;
import com.flutter.gbp.betcontext.domain.ImmutableRampSelection;
import com.flutter.gbp.betcontext.domain.ImmutableSameGameGroup;
import com.flutter.gbp.betcontext.domain.ImmutableSgmContext;
import com.flutter.gbp.betcontext.domain.ImmutableSgmSelection;
import com.flutter.gbp.betcontext.domain.ImmutableSportsbookSelection;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class SgmEnricherTest {

    @Mock
    private SgmGroupPriceConverter sgmGroupPriceConverter;

    private SgmEnricher victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        victim = new SgmEnricher(sgmGroupPriceConverter);
        when(sgmGroupPriceConverter.convertSgmGroupPrice(1.5d)).thenReturn(DoubleValue.of(1.5d));
        when(sgmGroupPriceConverter.convertSgmGroupPrice(3.5d)).thenReturn(DoubleValue.of(3.5d));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldTestInvalidArguments() {
        new SgmEnricher(null);
    }

    @Test
    public void testEnrichWithSgmData_NoFeatures() {
        Bet.Builder betBuilder = Bet.newBuilder();

        BetContext context = mock(BetContext.class);

        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder().build();

        victim.enrich(bet,context, betBuilder);

        // No SGM groups were added
        assertEquals(betBuilder.getSgmGroupsCount(), 0);
    }

    @Test
    public void testEnrichWithSgmData_GroupsPresentByRampId() {
        BetOuterClass.Bet bet = getFbpBetWithRampSelections();
        Bet.Builder betBuilder = getBetBuilder();

        ImmutableSgmSelection sgmSelection1 =
                ImmutableSgmSelection.builder().withRampSelection(ImmutableRampSelection.builder().withSelectionRampId("100").build()).build();
        ImmutableSameGameGroup sameGameGroup1 = ImmutableSameGameGroup.builder()
                .withGroupId("1")
                .withPrice(ImmutablePrice.builder().withDecimal(1.5).build())
                .withProbability(Optional.of(0.01d))
                .build();

        ImmutableSgmSelection sgmSelection2 =
                ImmutableSgmSelection.builder().withRampSelection(ImmutableRampSelection.builder().withSelectionRampId("200").build()).build();
        ImmutableSameGameGroup sameGameGroup2 = ImmutableSameGameGroup.builder()
                .withGroupId("2")
                .withPrice(ImmutablePrice.builder().withDecimal(3.5).build())
                .withProbability(Optional.of(0.02d))
                .build();


        BetContext betContext = ImmutableBetContext.builder()
                .withSgmContext(ImmutableSgmContext.builder()
                        .withSelectionsGroup(Map.of(sgmSelection1, sameGameGroup1, sgmSelection2, sameGameGroup2))
                        .build())
                .build();

        victim.enrich(bet, betContext, betBuilder);

        // Verify that sgmGroups are added to betBuilder
        assertEquals(betBuilder.getSgmGroupsCount(), 2);
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getRef().getValue(), "1");
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getPrice(), DoubleValue.of(1.5d));
        assertEquals(betBuilder.getSgmGroups(0).getPlacementProbability(), DoubleValue.of(0.01d));
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getRef().getValue(), "2");
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getPrice(), DoubleValue.of(3.5d));
        assertEquals(betBuilder.getSgmGroups(1).getPlacementProbability(), DoubleValue.of(0.02d));

        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRef(0), StringValue.of("1"));
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
    }

    @Test
    public void testEnrichWithSgmData_SameGroupsPresentForDifferentSelection() {
        BetOuterClass.Bet bet = getFbpBetWithSportsbookSelections("SPORTEX");
        Bet.Builder betBuilder = getBetBuilder();

        ImmutableSgmSelection sgmSelection1 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("300")
                                .withMarketId("3001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup1 = ImmutableSameGameGroup.builder()
                .withGroupId("1")
                .withPrice(ImmutablePrice.builder().withDecimal(1.5).build())
                .withProbability(Optional.of(0.01d))
                .build();

        ImmutableSgmSelection sgmSelection2 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("400")
                                .withMarketId("4001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup2 = ImmutableSameGameGroup.builder()
                .withGroupId("2")
                .withPrice(ImmutablePrice.builder().withDecimal(3.5).build())
                .withProbability(Optional.of(0.02d))
                .build();

        ImmutableSgmSelection sgmSelection3 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("500")
                                .withMarketId("5001")
                                .build())
                        .build();

        BetContext betContext = ImmutableBetContext.builder()
                .withSgmContext(ImmutableSgmContext.builder()
                        .withSelectionsGroup(Map.of(sgmSelection1, sameGameGroup1, sgmSelection2, sameGameGroup2, sgmSelection3, sameGameGroup2))
                        .build())
                .build();

        victim.enrich(bet, betContext, betBuilder);

        // Verify that sgmGroups are added to betBuilder
        assertEquals(betBuilder.getSgmGroupsCount(), 2);
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getRef().getValue(), "1");
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getPrice(), DoubleValue.of(1.5d));
        assertEquals(betBuilder.getSgmGroups(0).getPlacementProbability(), DoubleValue.of(0.01d));
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getRef().getValue(), "2");
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getPrice(), DoubleValue.of(3.5d));
        assertEquals(betBuilder.getSgmGroups(1).getPlacementProbability(), DoubleValue.of(0.02d));

        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRef(0), StringValue.of("1"));
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
        assertEquals(betBuilder.getLegs(2).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(2).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
    }

    @Test
    public void testEnrichWithSgmData_SameGroupsPresentForSameSelectionsWithDifferentMarkets() {
        BetOuterClass.Bet bet = getFbpBetWithSameSportsbookSelectionsWithDifferentMarkets();
        Bet.Builder betBuilder = getBetBuilder();

        ImmutableSgmSelection sgmSelection1 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("300")
                                .withMarketId("3001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup1 = ImmutableSameGameGroup.builder()
                .withGroupId("1")
                .withPrice(ImmutablePrice.builder().withDecimal(1.5).build())
                .withProbability(Optional.of(0.01d))
                .build();

        ImmutableSgmSelection sgmSelection2 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("400")
                                .withMarketId("4001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup2 = ImmutableSameGameGroup.builder()
                .withGroupId("2")
                .withPrice(ImmutablePrice.builder().withDecimal(3.5).build())
                .withProbability(Optional.of(0.02d))
                .build();

        ImmutableSgmSelection sgmSelection3 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("400")
                                .withMarketId("5001")
                                .build())
                        .build();

        BetContext betContext = ImmutableBetContext.builder()
                .withSgmContext(ImmutableSgmContext.builder()
                        .withSelectionsGroup(Map.of(sgmSelection1, sameGameGroup1, sgmSelection2, sameGameGroup2, sgmSelection3, sameGameGroup2))
                        .build())
                .build();

        victim.enrich(bet, betContext, betBuilder);

        // Verify that sgmGroups are added to betBuilder
        assertEquals(betBuilder.getSgmGroupsCount(), 2);
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getRef().getValue(), "1");
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getPrice(), DoubleValue.of(1.5d));
        assertEquals(betBuilder.getSgmGroups(0).getPlacementProbability(), DoubleValue.of(0.01d));
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getRef().getValue(), "2");
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getPrice(), DoubleValue.of(3.5d));
        assertEquals(betBuilder.getSgmGroups(1).getPlacementProbability(), DoubleValue.of(0.02d));

        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRef(0), StringValue.of("1"));
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
        assertEquals(betBuilder.getLegs(2).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(2).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
    }

    @Test
    public void testEnrichWithSgmData_GroupsPresentBySportsbookId() {
        BetOuterClass.Bet bet = getFbpBetWithSportsbookSelections("SPORTEX");
        Bet.Builder betBuilder = getBetBuilder();

        ImmutableSgmSelection sgmSelection1 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("300")
                                .withMarketId("3001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup1 = ImmutableSameGameGroup.builder()
                .withGroupId("1")
                .withPrice(ImmutablePrice.builder().withDecimal(1.5).build())
                .withProbability(Optional.of(0.01d))
                .build();

        ImmutableSgmSelection sgmSelection2 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("400")
                                .withMarketId("4001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup2 = ImmutableSameGameGroup.builder()
                .withGroupId("2")
                .withPrice(ImmutablePrice.builder().withDecimal(3.5).build())
                .withProbability(Optional.of(0.02d))
                .build();


        BetContext betContext = ImmutableBetContext.builder()
                .withSgmContext(ImmutableSgmContext.builder()
                        .withSelectionsGroup(Map.of(sgmSelection1, sameGameGroup1, sgmSelection2, sameGameGroup2))
                        .build())
                .build();

        victim.enrich(bet, betContext, betBuilder);

        // Verify that sgmGroups are added to betBuilder
        assertEquals(betBuilder.getSgmGroupsCount(), 2);
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getRef().getValue(), "1");
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getPrice(), DoubleValue.of(1.5d));
        assertEquals(betBuilder.getSgmGroups(0).getPlacementProbability(), DoubleValue.of(0.01d));
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getRef().getValue(), "2");
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getPrice(), DoubleValue.of(3.5d));
        assertEquals(betBuilder.getSgmGroups(1).getPlacementProbability(), DoubleValue.of(0.02d));

        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRef(0), StringValue.of("1"));
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
    }

    @Test
    public void testEnrichWithSgmData_GroupsPresentByBmsId() {
        BetOuterClass.Bet bet = getFbpBetWithSportsbookSelections("OPENBET");
        Bet.Builder betBuilder = getBetBuilder();

        ImmutableSgmSelection sgmSelection1 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("300")
                                .withMarketId("3001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup1 = ImmutableSameGameGroup.builder()
                .withGroupId("1")
                .withPrice(ImmutablePrice.builder().withDecimal(1.5).build())
                .withProbability(Optional.of(0.01d))
                .build();

        ImmutableSgmSelection sgmSelection2 =
                ImmutableSgmSelection.builder()
                        .withSportsbookSelection(ImmutableSportsbookSelection.builder()
                                .withSelectionId("400")
                                .withMarketId("4001")
                                .build())
                        .build();
        ImmutableSameGameGroup sameGameGroup2 = ImmutableSameGameGroup.builder()
                .withGroupId("2")
                .withPrice(ImmutablePrice.builder().withDecimal(3.5).build())
                .withProbability(Optional.of(0.02d))
                .build();


        BetContext betContext = ImmutableBetContext.builder()
                .withSgmContext(ImmutableSgmContext.builder()
                        .withSelectionsGroup(Map.of(sgmSelection1, sameGameGroup1, sgmSelection2, sameGameGroup2))
                        .build())
                .build();

        victim.enrich(bet, betContext, betBuilder);

        // Verify that sgmGroups are added to betBuilder
        assertEquals(betBuilder.getSgmGroupsCount(), 2);
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getRef().getValue(), "1");
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getPrice(), DoubleValue.of(1.5d));
        assertEquals(betBuilder.getSgmGroups(0).getPlacementProbability(), DoubleValue.of(0.01d));
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getRef().getValue(), "2");
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getPrice(), DoubleValue.of(3.5d));
        assertEquals(betBuilder.getSgmGroups(1).getPlacementProbability(), DoubleValue.of(0.02d));

        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRef(0), StringValue.of("1"));
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
    }

    @DataProvider
    public static Object[][] simpleSgmDataProvider() {
        return new Object[][]{
                {null, null},
                {1.5, null},
                {1.5, 1.2},
        };
    }

    private Bet.Builder getBetBuilder() {
        Bet.Builder betBuilder = Bet.newBuilder();

        Leg.Builder legBuilder = Leg.newBuilder().setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(1).build());
        Leg.Builder legBuilder2 = Leg.newBuilder().setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(2).build());
        Leg.Builder legBuilder3 = Leg.newBuilder().setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(3).build());

        betBuilder.addAllLegs(List.of(legBuilder.build(), legBuilder2.build(), legBuilder3.build()));

        return betBuilder;
    }

    private BetOuterClass.Bet getFbpBetWithRampSelections() {
        PartOuterClass.Part part1 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("RAMP", "100")
                                        .build())
                                .build())
                        .build();
        PartOuterClass.Part part2 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("RAMP", "200")
                                        .build())
                                .build())
                        .build();
        LegOuterClass.Leg leg1 = LegOuterClass.Leg.newBuilder().setLegNumber(1).addPart(part1).build();
        LegOuterClass.Leg leg2 = LegOuterClass.Leg.newBuilder().setLegNumber(2).addPart(part2).build();
        return BetOuterClass.Bet.newBuilder()
                .addLegs(leg1)
                .addLegs(leg2)
                .setBetContext(StringValue.of("RDA21412DA5123$%D12"))
                .build();
    }

    private BetOuterClass.Bet getFbpBetWithSportsbookSelections(String selectionIdentifier) {
        PartOuterClass.Part.Builder partBuilder1 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds(selectionIdentifier, "300")
                                        .build())
                                .build())
                        .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTSBOOK", "3001")
                                        .build())
                                .build());

        PartOuterClass.Part.Builder partBuilder2 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds(selectionIdentifier, "400")
                                        .build())
                                .build())
                        .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTSBOOK", "4001")
                                        .build())
                                .build());

        PartOuterClass.Part.Builder partBuilder3 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds(selectionIdentifier, "500")
                                        .build())
                                .build())
                        .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTSBOOK", "5001")
                                        .build())
                                .build());

        // If OPENBET is used, set SPORTEX to "0" to trigger fallback logic
        if ("OPENBET".equals(selectionIdentifier)) {
            partBuilder1.getSelectionInfoBuilder().getEntityBuilder().putIds("SPORTEX", "0");
            partBuilder2.getSelectionInfoBuilder().getEntityBuilder().putIds("SPORTEX", "0");
            partBuilder3.getSelectionInfoBuilder().getEntityBuilder().putIds("SPORTEX", "0");
        }

        PartOuterClass.Part part1 = partBuilder1.build();
        PartOuterClass.Part part2 = partBuilder2.build();
        PartOuterClass.Part part3 = partBuilder3.build();

        LegOuterClass.Leg leg1 = LegOuterClass.Leg.newBuilder().setLegNumber(1).addPart(part1).build();
        LegOuterClass.Leg leg2 = LegOuterClass.Leg.newBuilder().setLegNumber(2).addPart(part2).build();
        LegOuterClass.Leg leg3 = LegOuterClass.Leg.newBuilder().setLegNumber(3).addPart(part3).build();

        return BetOuterClass.Bet.newBuilder()
                .addAllLegs(java.util.List.of(leg1, leg2, leg3))
                .setBetContext(com.google.protobuf.StringValue.of("RDA21412DA5123$%D12"))
                .setCombinedPrice(CombinedPriceOuterClass.CombinedPrice.newBuilder()
                        .setOriginalDecimal(com.google.protobuf.DoubleValue.of(1.2))
                        .build())
                .build();
    }

    private BetOuterClass.Bet getFbpBetWithSameSportsbookSelectionsWithDifferentMarkets() {
        PartOuterClass.Part part1 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTEX", "300")
                                        .build())
                                .build())
                        .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTSBOOK", "3001")
                                        .build())
                                .build())
                        .build();
        PartOuterClass.Part part2 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTEX", "400")
                                        .build())
                                .build())
                        .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTSBOOK", "4001")
                                        .build())
                                .build())
                        .build();
        PartOuterClass.Part part3 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTEX", "400")
                                        .build())
                                .build())
                        .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                                .setEntity(PartOuterClass.Entity.newBuilder()
                                        .putIds("SPORTSBOOK", "5001")
                                        .build())
                                .build())
                        .build();
        LegOuterClass.Leg leg1 = LegOuterClass.Leg.newBuilder().setLegNumber(1).addPart(part1).build();
        LegOuterClass.Leg leg2 = LegOuterClass.Leg.newBuilder().setLegNumber(2).addPart(part2).build();
        LegOuterClass.Leg leg3 = LegOuterClass.Leg.newBuilder().setLegNumber(3).addPart(part3).build();
        return BetOuterClass.Bet.newBuilder()
                .addAllLegs(List.of(leg1, leg2, leg3))
                .setBetContext(StringValue.of("RDA21412DA5123$%D12"))
                .setCombinedPrice(CombinedPriceOuterClass.CombinedPrice.newBuilder()
                        .setOriginalDecimal(DoubleValue.of(1.2))
                        .build())
                .build();
    }
}