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
import com.flutter.gbp.fcq.bet.outcome.details.ChoiceResult;
import com.flutter.gbp.fcq.bet.outcome.details.OutcomeBasedDetailsOuterClass;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class OutcomeBasedLegConverterTest {

    private static final String BET_ID = "O/123456789/0001";
    private static final String SELECTION_RAMP_ID = "1";
    private static final String EVENT_RAMP_ID = "2";
    private static final String RAMP_IDENTIFIER = "RAMP";
    private static final String MISC = "MISC";
    private static final String MISC_FEED_ID = "misc_feed_id";
    private static final String BMS_IDENTIFIER = "OPENBET";
    private static final String BMS_ID = "1234";
    private static final String CHOICE_IDENTIFIER = "CHOICE";

    @Mock
    private OutcomeBasedLegQuotingDataConverter legQuotingDataConverter;
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
    private OutcomeBasedLegConverterImpl victim;

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
    public void testConvert(LegOuterClass.Leg leg, boolean marketBasedObpBetsEnabled, String expectedBmsId) {
        // Mock the behavior of dependent components
        when(legQuotingDataConverter.convert(leg, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_SPORT_NAME")).thenReturn(StringValue.of("MOCK_SPORT_NAME"));
        when(oddsConverter.convert(FractionOuterClass.Fraction.newBuilder()
                .setNumerator(11)
                .setDenominator(13)
                .build())).thenReturn(Odds.getDefaultInstance());
        when(enableMarketBasedObpBetsToggle.getAsBoolean()).thenReturn(marketBasedObpBetsEnabled);
        when(outcomeBasedDetailsConverter.convert(any(), eq(EVENT_RAMP_ID)))
                .thenReturn(OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.newBuilder().build());

        // Perform the conversion
        List<Leg> results = victim.convert(leg, BET_ID);

        // Assertions
        assertEquals(results.size(), 1);
        Leg result = results.get(0);
        assertNotNull(result);
        assertEquals(result.getLegType(), LegType.OB);
        assertEquals(result.getEventId(), Int64Value.of(12313));
        assertEquals(result.getEventRampId(), StringValue.of(EVENT_RAMP_ID));
        assertEquals(result.getSportName(), StringValue.of("MOCK_SPORT_NAME"));
        assertEquals(result.getStartingPrice(), Odds.getDefaultInstance());
        // A multiple choice group is never SuperSub eligible, so the converter is not even told which ramp ids are.
        assertFalse(result.getIsSuperSubEligible());
        assertEquals(result.getOutcomeBasedDetails(), OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.getDefaultInstance());
        assertPartLevelAttributesAreNotCopiedOntoTheLeg(result);
    }

    /**
     * Asserts that the Part level attributes carried by the first Part are no longer copied onto the leg: an
     * outcome-based leg is a multiple choice group, so those attributes belong to the individual Parts of the group
     * (carried by the quoting data Part list) and not to the leg itself.
     *
     * @param result the converted CBD leg
     */
    private void assertPartLevelAttributesAreNotCopiedOntoTheLeg(Leg result) {
        assertFalse(result.hasExchangeMarketId());
        assertFalse(result.hasHandicap());
        assertFalse(result.hasFrontEndEachwayFactor());
        assertFalse(result.hasEventDescription());
        assertFalse(result.hasEventMarketDescription());
        assertFalse(result.hasSelectionName());
        assertFalse(result.hasStartTime());
        assertFalse(result.hasCompetitionName());
        assertEquals(result.getEachwayPlaces(), 0);
        verifyNoInteractions(fractionalOddsConverter);
        verify(javaValueToProtoValueConverter, never()).convertToTimestamp(anyLong());
    }

    @Test
    public void testConvertTurnsAMultiplePartLegIntoASingleLeg() {
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(buildSelectionPart(RAMP_IDENTIFIER, SELECTION_RAMP_ID))
                .addPart(buildSelectionPart(CHOICE_IDENTIFIER, "choice-1"))
                .addPart(buildSelectionPart(CHOICE_IDENTIFIER, "choice-2"))
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);

        List<Leg> results = victim.convert(leg, BET_ID);

        // The whole multiple choice group is one quoted unit, so it becomes exactly one CBD Leg.
        assertEquals(results.size(), 1);
        // The quoting data is converted once, from the leg: carrying every Part of the group is its converter's job.
        verify(legQuotingDataConverter, times(1)).convert(leg, BET_ID);
    }

    @Test
    public void testConvertTakesTheSportNameFromTheFirstPartThatCarriesOne() {
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(buildSelectionPart(CHOICE_IDENTIFIER, "choice-1"))
                .addPart(buildPartWithSportName("MOCK_SPORT_NAME"))
                .addPart(buildPartWithSportName("ANOTHER_MOCK_SPORT_NAME"))
                .build();

        when(legQuotingDataConverter.convert(any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);
        when(javaValueToProtoValueConverter.convertToStringValue("MOCK_SPORT_NAME")).thenReturn(StringValue.of("MOCK_SPORT_NAME"));

        Leg result = victim.convert(leg, BET_ID).get(0);

        // The sport name is not guaranteed to be on the first Part, so the first Part carrying one wins.
        assertEquals(result.getSportName(), StringValue.of("MOCK_SPORT_NAME"));
    }

    @Test
    public void testConvertLeavesTheSportNameUnsetWhenNoPartCarriesOne() {
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(buildSelectionPart(CHOICE_IDENTIFIER, "choice-1"))
                .addPart(buildPartWithSportName(""))
                .build();

        when(legQuotingDataConverter.convert(any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);

        Leg result = victim.convert(leg, BET_ID).get(0);

        assertFalse(result.hasSportName());
        verifyNoInteractions(javaValueToProtoValueConverter);
    }

    @Test
    public void testConvertReturnsNoLegsWhenTheLegCarriesNoParts() {
        LegOuterClass.Leg leg = outcomeBasedLegBuilder().build();

        List<Leg> results = victim.convert(leg, BET_ID);

        assertTrue(results.isEmpty());
        verifyNoInteractions(legQuotingDataConverter);
        verifyNoInteractions(outcomeBasedDetailsConverter);
    }

    @Test
    public void testConvertResolvesOutcomeBasedDetailsAgainstTheFirstPartEventRampId() {
        PartOuterClass.Part firstPart = buildLegPart(Map.of(RAMP_IDENTIFIER, SELECTION_RAMP_ID), false, false);
        OutcomeBasedDetails fbpOutcomeBasedDetails = OutcomeBasedDetails.newBuilder().build();
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(firstPart)
                .setOutcomeBasedDetails(fbpOutcomeBasedDetails)
                .build();
        OutcomeBasedDetailsOuterClass.OutcomeBasedDetails convertedDetails = OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.newBuilder()
                .addChoices(ChoiceOuterClass.Choice.newBuilder().setChoiceNumber(1).build())
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);
        when(outcomeBasedDetailsConverter.convert(fbpOutcomeBasedDetails, EVENT_RAMP_ID)).thenReturn(convertedDetails);

        List<Leg> results = victim.convert(leg, BET_ID);

        assertEquals(results.get(0).getOutcomeBasedDetails(), convertedDetails);
        verify(outcomeBasedDetailsConverter).convert(fbpOutcomeBasedDetails, EVENT_RAMP_ID);
    }

    @Test
    public void testConvertLeavesOutcomeBasedDetailsUnsetWhenTheLegCarriesNone() {
        PartOuterClass.Part part = buildSelectionPart(CHOICE_IDENTIFIER, "choice-1");
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(part)
                .build();

        stubScalarConverters();
        when(legQuotingDataConverter.convert(any(), any())).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);

        List<Leg> results = victim.convert(leg, BET_ID);

        assertFalse(results.get(0).hasOutcomeBasedDetails());
        verifyNoInteractions(outcomeBasedDetailsConverter);
    }

    @Test
    public void testConvertDoesNotOverwriteChoiceResultWithPartOrLegResult() {
        PartOuterClass.Part sourcePart = PartOuterClass.Part.newBuilder()
                .setResult(PartOuterClass.Part.Result.LOST)
                .build();
        LegOuterClass.Leg sourceLeg = outcomeBasedLegBuilder()
                .addPart(sourcePart)
                .setResult(LegOuterClass.Leg.Result.WON)
                .setOutcomeBasedDetails(OutcomeBasedDetails.newBuilder().build())
                .build();
        OutcomeBasedDetailsOuterClass.OutcomeBasedDetails convertedDetails = OutcomeBasedDetailsOuterClass.OutcomeBasedDetails.newBuilder()
                .addChoices(ChoiceOuterClass.Choice.newBuilder()
                        .setChoiceNumber(1)
                        .setResult(ChoiceResult.VOIDED)
                        .build())
                .build();

        when(legQuotingDataConverter.convert(sourceLeg, BET_ID))
                .thenReturn(LegQuotingData.newBuilder().setLegStatus(LegStatus.LOST).build());
        when(legTypeConverter.convert(sourceLeg.getLegType())).thenReturn(LegType.OB);
        when(outcomeBasedDetailsConverter.convert(any(), any())).thenReturn(convertedDetails);

        Leg result = victim.convert(sourceLeg, BET_ID).get(0);

        assertEquals(result.getLegQuotingData().getLegStatus(), LegStatus.LOST);
        assertEquals(result.getOutcomeBasedDetails().getChoices(0).getResult(), ChoiceResult.VOIDED);
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
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(part)
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(legStartingPrice)
                        .build())
                .build();
        Odds legOdds = Odds.newBuilder().setFractionalDisplayOdds(FractionalOdds.newBuilder().build()).build();

        when(legQuotingDataConverter.convert(leg, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);
        when(oddsConverter.convert(legStartingPrice)).thenReturn(legOdds);

        Leg result = victim.convert(leg, BET_ID).get(0);

        assertEquals(result.getStartingPrice(), legOdds);
        verify(oddsConverter, never()).convert(part.getFractionalPrice().getStarting());
    }

    @Test
    public void testConvertFallsBackToThePartFractionalPriceStartingWhenTheLegCarriesASinglePart() {
        FractionOuterClass.Fraction partStartingPrice = FractionOuterClass.Fraction.newBuilder()
                .setNumerator(11)
                .setDenominator(13)
                .build();
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(PartOuterClass.Part.newBuilder()
                        .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                                .setStarting(partStartingPrice)
                                .build())
                        .build())
                .build();
        Odds partOdds = Odds.newBuilder().setFractionalDisplayOdds(FractionalOdds.newBuilder().build()).build();

        when(legQuotingDataConverter.convert(leg, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);
        when(oddsConverter.convert(partStartingPrice)).thenReturn(partOdds);

        Leg result = victim.convert(leg, BET_ID).get(0);

        // The single part's price is the price of the whole group, so it is a valid fallback for the leg price.
        assertEquals(result.getStartingPrice(), partOdds);
    }

    @Test
    public void testConvertLeavesStartingPriceUnsetWhenTheLegCarriesNoPriceAndMoreThanOnePart() {
        PartOuterClass.Part pricedPart = PartOuterClass.Part.newBuilder()
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setStarting(FractionOuterClass.Fraction.newBuilder().setNumerator(11).setDenominator(13).build())
                        .build())
                .build();
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(pricedPart)
                .addPart(buildSelectionPart(CHOICE_IDENTIFIER, "choice-1"))
                .build();

        when(legQuotingDataConverter.convert(leg, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);

        Leg result = victim.convert(leg, BET_ID).get(0);

        // No individual part price represents the combined price of a multiple choice group, so none is used.
        assertEquals(result.getStartingPrice(), Odds.getDefaultInstance());
        verifyNoInteractions(oddsConverter);
    }

    @Test
    public void testConvertLeavesStartingPriceUnsetWhenNeitherLegNorPartHaveFractionalPrice() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder().build();
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(part)
                .build();

        when(legQuotingDataConverter.convert(leg, BET_ID)).thenReturn(LegQuotingData.newBuilder().build());
        when(legTypeConverter.convert(LegOuterClass.Leg.LegType.OUTCOME_BASED)).thenReturn(LegType.OB);

        Leg result = victim.convert(leg, BET_ID).get(0);

        assertEquals(result.getStartingPrice(), Odds.getDefaultInstance());
    }

    /**
     * Stubs the scalar converters shared by every mapped field, so that tests focused on leg expansion do not have
     * to stub each individual field conversion.
     */
    private void stubScalarConverters() {
        when(javaValueToProtoValueConverter.convertToStringValue(any())).thenReturn(StringValue.getDefaultInstance());
        when(oddsConverter.convert(any(FractionOuterClass.Fraction.class))).thenReturn(Odds.getDefaultInstance());
    }

    @DataProvider
    public Object[][] testConvertDataProvider() {
        return new Object[][]{
                {getDefaultLeg(true, true), true, ""},
                {getDefaultLeg(false, true), false, ""},
                {getFdObpBasedLeg(), true, BMS_ID}
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
        return outcomeBasedLegBuilder()
                .addPart(buildLegPart(selectionInfoEntityIdsMap, withExchangeMarketId, withMiscFeedId))
                .setOutcomeBasedDetails(OutcomeBasedDetails.newBuilder().build())
                .build();
    }

    private LegOuterClass.Leg.Builder outcomeBasedLegBuilder() {
        return LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.OUTCOME_BASED);
    }

    private PartOuterClass.Part buildPartWithSportName(String sportName) {
        return PartOuterClass.Part.newBuilder()
                .setSportInfo(PartOuterClass.SportInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .setName(sportName)
                                .build())
                        .build())
                .build();
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

    private PartOuterClass.Part buildLegPart(Map<String, String> selectionInfoEntityIdsMap, boolean withExchangeMarketId,
                                             boolean withMiscFeedId) {
        Map<String, String> selectionIds = new java.util.HashMap<>(selectionInfoEntityIdsMap);
        if (withMiscFeedId) {
            selectionIds.put(MISC, MISC_FEED_ID);
        }

        return PartOuterClass.Part.newBuilder()
                .setEventInfo(PartOuterClass.EventInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds("SPORTEX", "12313").putIds(RAMP_IDENTIFIER, EVENT_RAMP_ID)
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
