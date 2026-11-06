package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.fbp.FractionalOddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.OddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.OutcomeBasedDetailsConverter;
import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.flutter.gbp.fcq.bet.leg.LegType;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceOuterClass;
import com.flutter.gbp.fcq.bet.outcome.details.OutcomeBasedDetailsOuterClass;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.outcomebaseddetails.OutcomeBasedDetailsOuterClass.OutcomeBasedDetails;
import com.ppb.platform.sb.fbp.proto.bet.part.EachwayInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import java.util.function.BooleanSupplier;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class MarketBasedLegConverterTest {

    private static final String BET_ID = "O/123456789/0001";
    private static final String SELECTION_RAMP_ID = "1";
    private static final String EVENT_RAMP_ID = "2";
    private static final String RAMP_IDENTIFIER = "RAMP";
    private static final String MISC = "MISC";
    private static final String MISC_FEED_ID = "misc_feed_id";
    private static final String BMS_IDENTIFIER = "OPENBET";
    private static final String BMS_ID = "1234";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";

    @Mock
    private MarketBasedLegQuotingDataConverter legQuotingDataConverter;
    @Mock
    private OddsConverter oddsConverter;
    @Mock
    private FractionalOddsConverter fractionalOddsConverter;
    @Mock
    private LegTypeConverter legTypeConverter;
    @Mock
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Mock
    private OutcomeBasedDetailsConverter outcomeBasedDetailsConverter;
    @Mock
    private BooleanSupplier enableMarketBasedObpBetsToggle;
    @InjectMocks
    private MarketBasedLegConverterImpl victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(dataProvider = "testConvertDataProvider")
    public void testConvert(LegOuterClass.Leg leg, boolean expectedHasExchangeMarketId, Set<String> superSubEligibleRampIds,
                            boolean expectedMiscFeedIdMapping, String expectedBmsId) {
        // Mock the behavior of dependent components
        when(legQuotingDataConverter.convert(leg, leg.getPart(0), BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);
        when(javaValueToProtoValueConverter.convertToTimestamp(1681551000L)).thenReturn(Timestamp.getDefaultInstance());
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_EVENT_DESCRIPTION")).thenReturn(StringValue.of("MOCK_EVENT_DESCRIPTION"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_MARKET_DESCRIPTION")).thenReturn(StringValue.of("MOCK_MARKET_DESCRIPTION"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_SELECTION_NAME")).thenReturn(StringValue.of("MOCK_SELECTION_NAME"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_SPORT_NAME")).thenReturn(StringValue.of("MOCK_SPORT_NAME"));
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_COMPETITION_NAME")).thenReturn(StringValue.of("MOCK_COMPETITION_NAME"));
        when(oddsConverter.convert(FractionOuterClass.Fraction.newBuilder()
                .setNumerator(11)
                .setDenominator(13)
                .build())).thenReturn(Odds.getDefaultInstance());
        when(fractionalOddsConverter.convert(FractionOuterClass.Fraction.newBuilder()
                .setNumerator(4)
                .setDenominator(7)
                .build()))
                .thenReturn(FractionalOdds.getDefaultInstance());
        when(enableMarketBasedObpBetsToggle.getAsBoolean()).thenReturn(expectedMiscFeedIdMapping);

        // Perform the conversion
        List<Leg> results = victim.convert(leg, superSubEligibleRampIds, BET_ID);

        // Assertions
        assertEquals(results.size(), 1);
        Leg result = results.get(0);
        assertNotNull(result);
        assertEquals(result.getLegType(), LegType.SS);
        assertEquals(result.getEventId(), Int64Value.of(12313));
        assertEquals(result.getEventRampId(), StringValue.of(EVENT_RAMP_ID));
        assertEquals(result.hasExchangeMarketId(), expectedHasExchangeMarketId);
        if (expectedHasExchangeMarketId) {
            assertEquals(result.getExchangeMarketId(), StringValue.of("1.213123"));
        } else {
            assertEquals(result.getExchangeMarketId(), StringValue.getDefaultInstance());
        }
        assertEquals(result.getEachwayPlaces(), 3);
        assertEquals(result.getEventDescription(), StringValue.of("MOCK_EVENT_DESCRIPTION"));
        assertEquals(result.getEventMarketDescription(), StringValue.of("MOCK_MARKET_DESCRIPTION"));
        assertEquals(result.getSelectionName(), StringValue.of("MOCK_SELECTION_NAME"));
        assertEquals(result.getHandicap(), DoubleValue.of(2d));
        assertEquals(result.getSportName(), StringValue.of("MOCK_SPORT_NAME"));
        assertEquals(result.getCompetitionName(), StringValue.of("MOCK_COMPETITION_NAME"));
        assertEquals(result.getStartingPrice(), Odds.getDefaultInstance());
        assertEquals(result.getFrontEndEachwayFactor(), FractionalOdds.getDefaultInstance());
        assertEquals(result.getStartTime(), Timestamp.getDefaultInstance());
        assertEquals(result.getIsSuperSubEligible(), superSubEligibleRampIds.contains(SELECTION_RAMP_ID));
        assertEquals(result.getIsMarketBasedObpLeg(), expectedMiscFeedIdMapping);
        assertEquals(result.getSelectionBmsId(), StringValue.of(expectedBmsId));
    }

    @Test
    public void testConvertExpandsEveryPartIntoItsOwnLegQuotedFromThatPart() {
        PartOuterClass.Part firstPart = buildSelectionPart(SPORTEX_IDENTIFIER, "111");
        PartOuterClass.Part secondPart = buildSelectionPart(SPORTEX_IDENTIFIER, "222");
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(firstPart)
                .addPart(secondPart)
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(any(), any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);

        List<Leg> results = victim.convert(leg, Set.of(), BET_ID);

        // Each Part is an independent selection, so each one becomes its own CBD Leg quoted from only that Part.
        assertEquals(results.size(), 2);
        verify(legQuotingDataConverter).convert(leg, firstPart, BET_ID);
        verify(legQuotingDataConverter).convert(leg, secondPart, BET_ID);
    }

    @Test
    public void testConvertReturnsNoLegsWhenTheLegCarriesNoParts() {
        LegOuterClass.Leg leg = marketBasedLegBuilder().build();

        List<Leg> results = victim.convert(leg, Set.of(), BET_ID);

        assertTrue(results.isEmpty());
        verifyNoInteractions(legQuotingDataConverter);
    }

    @Test
    public void testConvertMapsTheLegQuotingDataOfEachPart() {
        PartOuterClass.Part part = buildSelectionPart(SPORTEX_IDENTIFIER, "111");
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(part)
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(leg, part, BET_ID))
                .thenReturn(LegQuotingData.newBuilder().setLegStatus(LegStatus.LOST).build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);

        Leg result = victim.convert(leg, Set.of(), BET_ID).get(0);

        assertEquals(result.getLegQuotingData().getLegStatus(), LegStatus.LOST);
    }

    @Test
    public void testConvertMapsTheOutcomeBasedDetailsOfAnObpEnrichedEmsLeg() {
        PartOuterClass.Part part = buildLegPart(Map.of(RAMP_IDENTIFIER, SELECTION_RAMP_ID), false, false);
        OutcomeBasedDetails fbpOutcomeBasedDetails = OutcomeBasedDetails.newBuilder().build();
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(part)
                .setOutcomeBasedDetails(fbpOutcomeBasedDetails)
                .build();
        OutcomeBasedDetailsOuterClass.OutcomeBasedDetails convertedDetails = OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.newBuilder()
                .addChoices(ChoiceOuterClass.Choice.newBuilder().setChoiceNumber(1).build())
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(any(), any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);
        when(outcomeBasedDetailsConverter.convert(fbpOutcomeBasedDetails, EVENT_RAMP_ID)).thenReturn(convertedDetails);

        Leg result = victim.convert(leg, Set.of(), BET_ID).get(0);

        // An EMS leg that has been OBP-enriched carries the choices and outcome expression that price it.
        assertEquals(result.getOutcomeBasedDetails(), convertedDetails);
        verify(outcomeBasedDetailsConverter).convert(fbpOutcomeBasedDetails, EVENT_RAMP_ID);
    }

    @Test
    public void testConvertLeavesOutcomeBasedDetailsUnsetWhenTheLegCarriesNone() {
        PartOuterClass.Part part = buildSelectionPart(SPORTEX_IDENTIFIER, "111");
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(part)
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(any(), any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);

        Leg result = victim.convert(leg, Set.of(), BET_ID).get(0);

        assertFalse(result.hasOutcomeBasedDetails());
        verifyNoInteractions(outcomeBasedDetailsConverter);
    }

    @Test
    public void testConvertUsesLegFractionalPriceStartingWhenPresentInsteadOfPartFractionalPrice() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(FractionOuterClass.Fraction.newBuilder().setNumerator(11).setDenominator(13).build())
                        .build())
                .build();
        FractionOuterClass.Fraction legStartingPrice = FractionOuterClass.Fraction.newBuilder()
                .setNumerator(5)
                .setDenominator(9)
                .build();
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(part)
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(legStartingPrice)
                        .build())
                .build();
        Odds legOdds = Odds.newBuilder().setFractionalDisplayOdds(FractionalOdds.newBuilder().build()).build();

        when(legQuotingDataConverter.convert(leg, part, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);
        when(oddsConverter.convert(legStartingPrice)).thenReturn(legOdds);

        Leg result = victim.convert(leg, Set.of(), BET_ID).get(0);

        assertEquals(result.getStartingPrice(), legOdds);
        verify(oddsConverter, never()).convert(part.getFractionalPrice().getStarting());
    }

    @Test
    public void testConvertAppliesTheLegFractionalPriceStartingToEveryLegExpandedFromAMultiPartLeg() {
        PartOuterClass.Part firstPart = buildPartWithStartingPrice(11, 13);
        PartOuterClass.Part secondPart = buildPartWithStartingPrice(3, 4);
        FractionOuterClass.Fraction legStartingPrice = FractionOuterClass.Fraction.newBuilder()
                .setNumerator(5)
                .setDenominator(9)
                .build();
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(firstPart)
                .addPart(secondPart)
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(legStartingPrice)
                        .build())
                .build();
        Odds legOdds = Odds.newBuilder().setFractionalDisplayOdds(FractionalOdds.newBuilder().build()).build();

        when(legQuotingDataConverter.convert(any(), any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);
        when(oddsConverter.convert(legStartingPrice)).thenReturn(legOdds);

        List<Leg> results = victim.convert(leg, Set.of(), BET_ID);

        // The leg level price takes precedence over each Part's own price, so every expanded leg carries the leg price
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).getStartingPrice(), legOdds);
        assertEquals(results.get(1).getStartingPrice(), legOdds);
        verify(oddsConverter, times(2)).convert(legStartingPrice);
        verify(oddsConverter, never()).convert(firstPart.getFractionalPrice().getStarting());
        verify(oddsConverter, never()).convert(secondPart.getFractionalPrice().getStarting());
    }

    @Test
    public void testConvertFallsBackToEachPartFractionalPriceStartingWhenAMultiPartLegHasNone() {
        PartOuterClass.Part firstPart = buildPartWithStartingPrice(11, 13);
        PartOuterClass.Part secondPart = buildPartWithStartingPrice(3, 4);
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(firstPart)
                .addPart(secondPart)
                .build();
        Odds firstPartOdds = Odds.newBuilder()
                .setFractionalDisplayOdds(FractionalOdds.newBuilder().setNumerator(Int32Value.of(11)).setDenominator(Int32Value.of(13)))
                .build();
        Odds secondPartOdds = Odds.newBuilder()
                .setFractionalDisplayOdds(FractionalOdds.newBuilder().setNumerator(Int32Value.of(3)).setDenominator(Int32Value.of(4)))
                .build();

        when(legQuotingDataConverter.convert(any(), any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);
        when(oddsConverter.convert(firstPart.getFractionalPrice().getStarting())).thenReturn(firstPartOdds);
        when(oddsConverter.convert(secondPart.getFractionalPrice().getStarting())).thenReturn(secondPartOdds);

        List<Leg> results = victim.convert(leg, Set.of(), BET_ID);

        // Without a leg level price each expanded leg is priced from its own Part
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).getStartingPrice(), firstPartOdds);
        assertEquals(results.get(1).getStartingPrice(), secondPartOdds);
    }

    @Test
    public void testConvertLeavesStartingPriceUnsetWhenNeitherLegNorPartHaveFractionalPrice() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder().build();
        LegOuterClass.Leg leg = marketBasedLegBuilder()
                .addPart(part)
                .build();

        when(legQuotingDataConverter.convert(leg, part, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)).thenReturn(LegType.SS);

        Leg result = victim.convert(leg, Set.of(), BET_ID).get(0);

        assertEquals(result.getStartingPrice(), Odds.getDefaultInstance());
    }

    /**
     * Stubs the scalar converters shared by every mapped field, so that tests focused on leg expansion do not have
     * to stub each individual field conversion.
     */
    private void stubScalarConverters() {
        when(javaValueToProtoValueConverter.convertToStringValue(any())).thenReturn(StringValue.getDefaultInstance());
        when(javaValueToProtoValueConverter.convertToTimestamp(anyLong())).thenReturn(Timestamp.getDefaultInstance());
        when(fractionalOddsConverter.convert(any())).thenReturn(FractionalOdds.getDefaultInstance());
        when(oddsConverter.convert(any(FractionOuterClass.Fraction.class))).thenReturn(Odds.getDefaultInstance());
    }

    @DataProvider
    public Object[][] testConvertDataProvider() {
        return new Object[][]{
                {getDefaultLeg(true, true), true, Set.of("1", "2"), true, ""},
                {getDefaultLeg(false, true), false, new HashSet<>(), false, ""},
                {getFdObpBasedLeg(), false, new HashSet<>(), true, BMS_ID}
        };
    }

    private LegOuterClass.Leg getDefaultLeg(boolean withExchangeMarketId, boolean withMiscFeedId) {
        Map<String, String> selectionInfoEntityIdsMap = new java.util.HashMap<>(Map.of(RAMP_IDENTIFIER, SELECTION_RAMP_ID));
        return buildLeg(selectionInfoEntityIdsMap, withExchangeMarketId, withMiscFeedId);
    }

    private LegOuterClass.Leg getFdObpBasedLeg() {
        Map<String, String> selectionInfoEntityIdsMap = new java.util.HashMap<>(Map.of(BMS_IDENTIFIER, BMS_ID));
        return buildLeg(selectionInfoEntityIdsMap, false, true);
    }

    private LegOuterClass.Leg buildLeg(Map<String, String> selectionInfoEntityIdsMap, boolean withExchangeMarketId, boolean withMiscFeedId) {
        return marketBasedLegBuilder()
                .addPart(buildLegPart(selectionInfoEntityIdsMap, withExchangeMarketId, withMiscFeedId))
                .build();
    }

    private LegOuterClass.Leg.Builder marketBasedLegBuilder() {
        return LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION);
    }

    private PartOuterClass.Part buildSelectionPart(String identifier, String id) {
        return PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(identifier, id)
                                .build())
                        .build())
                .build();
    }

    private PartOuterClass.Part buildPartWithStartingPrice(int numerator, int denominator) {
        return PartOuterClass.Part.newBuilder()
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(numerator)
                                .setDenominator(denominator)
                                .build())
                        .build())
                .build();
    }

    private PartOuterClass.Part buildLegPart(Map<String, String> selectionInfoEntityIdsMap, boolean withExchangeMarketId,
                                             boolean withMiscFeedId) {
        Map<String, String> selectionIds = new java.util.HashMap<>(selectionInfoEntityIdsMap);
        if (withMiscFeedId) {
            selectionIds.put(MISC, MISC_FEED_ID);
        }

        return PartOuterClass.Part.newBuilder()
                .setEventInfo(PartOuterClass.EventInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "12313").putIds(RAMP_IDENTIFIER, EVENT_RAMP_ID)
                                .setName("MOCK_EVENT_DESCRIPTION")
                                .build())
                        .setStartTime(1681551000L)
                        .build())
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(withExchangeMarketId ? "EXCHANGE" : "UNUSED", "1.213123")
                                .setName("MOCK_MARKET_DESCRIPTION")
                                .build())
                        .build())
                .setEachwayInfo(EachwayInfoOuterClass.EachwayInfo.newBuilder()
                        .setEachwayPlaces(3)
                        .setEachwayFactor(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(4)
                                .setDenominator(7)
                                .build())
                        .build())
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .setName("MOCK_SELECTION_NAME")
                                .putAllIds(selectionIds)
                                .build())
                        .build())
                .setHandicap(DoubleValue.of(2d))
                .setSportInfo(PartOuterClass.SportInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .setName("MOCK_SPORT_NAME")
                                .build())
                        .build())
                .setCompetitionInfo(PartOuterClass.CompetitionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .setName("MOCK_COMPETITION_NAME")
                                .build())
                        .build())
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(11)
                                .setDenominator(13)
                                .build())
                        .build())
                .build();
    }
}
