package com.flutter.gbp.betstore.writer.convert.streams.fbp.bet;

import com.flutter.gbp.betstore.writer.convert.streams.fbp.RiskContextConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.AccaFreezeEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.BetContextEnricherChain;
import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.EligibleJurisdictionConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.FundingTypeConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.OddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.MarketBasedLegConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.OutcomeBasedLegConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetStatus;
import com.flutter.gbp.fcq.bet.FundingType;
import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.flutter.gbp.fcq.bet.Platform;
import com.flutter.gbp.fcq.bet.RetailInfo;
import com.flutter.gbp.fcq.bet.SourceTag;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.flutter.gbp.fcq.bet.risk.RiskContext;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.BetPotentialWinOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass.CombinedPrice.CombinedPriceType;
import com.ppb.platform.sb.fbp.proto.bet.CurrentWinningsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.FeaturesOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.RiskContextOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.SourceTagOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.changeinterms.ChangeInTermsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.changeinterms.ChangeInTermsOuterClass.ChangeInTerms.Reason;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.status.BetStatusOuterClass;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

public class BetConverterTest {

    private static final String BET_ID = "O/123456789/0001";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String CHOICE_IDENTIFIER = "CHOICE";

    private static final Leg OUTCOME_BASED_LEG = Leg.newBuilder().setSelectionName(StringValue.of("outcome-based-leg")).build();
    private static final Leg MARKET_BASED_LEG = Leg.newBuilder().setSelectionName(StringValue.of("market-based-leg")).build();

    private static final RiskContext EXPECTED_RISK_CONTEXT = RiskContext.newBuilder()
        .setCustomerRisk(RiskContext.CustomerRisk.newBuilder()
            .setLiabilityGroup(StringValue.of("REGULAR CUSTOMER"))
            .build())
        .build();

    @Mock
    private BetQuotingDataConverter betQuotingDataConverter;
    @Mock
    private OutcomeBasedLegConverter outcomeBasedLegConverter;
    @Mock
    private MarketBasedLegConverter marketBasedLegConverter;
    @Mock
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Mock
    private OddsConverter oddsConverter;
    @Mock
    private BetStatusConverter betStatusConverter;
    @Mock
    private EligibleJurisdictionConverter eligibleJurisdictionConverter;
    @Mock
    private FundingTypeConverter fundingTypeConverter;
    @Mock
    private AccaFreezeEnricher accaFreezeEnricher;
    @Mock
    private BetContextEnricherChain betContextEnricherChain;
    @Mock
    private RiskContextConverter riskContextConverter;

    @InjectMocks
    private BetConverterImpl victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @DataProvider
    public static Object[][] dataProviderForFbpBetStream() {
        return new Object[][]{
                {CombinedPriceType.REGULAR, null, false, SourceTagOuterClass.SourceTag.OPENBET, SourceTag.OPENBET, true},
                {null, Reason.SAME_GAME_MULTIS, true, SourceTagOuterClass.SourceTag.GBP, SourceTag.GBP, false},
                {CombinedPriceType.SGM, null, true, null, SourceTag.OPENBET, true},
                {CombinedPriceType.SGM, Reason.SAME_GAME_MULTIS, true, null, SourceTag.OPENBET, false},
                {CombinedPriceType.REGULAR, null, false, SourceTagOuterClass.SourceTag.OBB, SourceTag.OBB, true},
                {CombinedPriceType.REGULAR, null, false, SourceTagOuterClass.SourceTag.UNSPECIFIED, SourceTag.OPENBET, false}
        };
    }

    @Test
    public void shouldHandleNullBetDomainEvent() {
        Bet result = victim.convert(null);
        assertNull(result);
    }

    @Test
    public void shouldHandleNullBet() {
        BetDomainEventOuterClass.BetDomainEvent betDomainEvent = BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setBetVersion(StringValue.of("10"))
                .clearBet()
                .build();

        Bet result = victim.convert(betDomainEvent);

        assertNotNull(result);
        // Should not call converters or enrichers when no bet is present
        verifyNoInteractions(betQuotingDataConverter);
        verifyNoInteractions(outcomeBasedLegConverter);
        verifyNoInteractions(marketBasedLegConverter);
        verifyNoInteractions(javaValueToProtoValueConverter);
        verifyNoInteractions(oddsConverter);
        verifyNoInteractions(betStatusConverter);
        verifyNoInteractions(eligibleJurisdictionConverter);
        verifyNoInteractions(fundingTypeConverter);
        verifyNoInteractions(accaFreezeEnricher);
        verifyNoInteractions(betContextEnricherChain);
        verifyNoInteractions(riskContextConverter);

    }

    @Test(dataProvider = "dataProviderForFbpBetStream")
    public void shouldConvertFbpBetStreamCorrectly(CombinedPriceType combinedPriceType,
                                                   Reason changeInTermsReason,
                                                   boolean expectedIsSameGameMultis,
                                                   SourceTagOuterClass.SourceTag sourceTag,
                                                   SourceTag expectedSourceTag,
                                                   boolean expectedHasEarlyWinReward) {
        CombinedPriceOuterClass.CombinedPrice combinedPrice = buildCombinedPrice(combinedPriceType);
        ChangeInTermsOuterClass.ChangeInTerms changeInTerms = buildChangeInTerms(changeInTermsReason);
        FeaturesOuterClass.Features features = buildFeatures(expectedHasEarlyWinReward);

        BetOuterClass.Bet.Builder bet = createTestBetBuilder();

        if (combinedPrice != null)
            bet.setCombinedPrice(combinedPrice);
        if (changeInTerms != null)
            bet.addChangeInTerms(changeInTerms);
        if (sourceTag != null)
            bet.setSourceTag(sourceTag);
        if (features != null)
            bet.setFeatures(features);

        BetDomainEventOuterClass.BetDomainEvent betDomainEvent = BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setBetVersion(StringValue.of("10"))
                .setRiskContext(RiskContextOuterClass.RiskContext.newBuilder().setCustomerRisk(RiskContextOuterClass.RiskContext.CustomerRisk.newBuilder()
                        .setLiabilityGroup(StringValue.of("Regular Customer"))
                        .build()))
                .setBet(bet)
                .build();

        // Mock the behavior of dependent components
        prepareMocks(betDomainEvent, combinedPrice);

        // Conversion
        Bet result = victim.convert(betDomainEvent);

        // Assertions
        assertConversionResult(result, betDomainEvent, expectedIsSameGameMultis,
                combinedPriceType != null, expectedSourceTag,
                expectedHasEarlyWinReward);
    }

    @Test
    public void shouldConvertOutcomeBasedLegWithTheOutcomeBasedLegConverter() {
        LegOuterClass.Leg outcomeBasedLeg = LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.OUTCOME_BASED)
                .addPart(buildPart(SPORTEX_IDENTIFIER, "111"))
                .addPart(buildPart(CHOICE_IDENTIFIER, "choice-1"))
                .build();

        Bet result = convertBetWith(outcomeBasedLeg);

        assertEquals(result.getLegsList(), List.of(OUTCOME_BASED_LEG));
        verify(outcomeBasedLegConverter, times(1)).convert(outcomeBasedLeg, BET_ID);
        verifyNoInteractions(marketBasedLegConverter);
    }

    @Test
    public void shouldConvertEveryOtherLegTypeWithTheMarketBasedLegConverter() {
        PartOuterClass.Part firstPart = buildPart(SPORTEX_IDENTIFIER, "111");
        PartOuterClass.Part secondPart = buildPart(SPORTEX_IDENTIFIER, "222");
        LegOuterClass.Leg marketBasedLeg = LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                .addPart(firstPart)
                .addPart(secondPart)
                .build();

        Bet result = convertBetWith(marketBasedLeg);

        // The market based converter expands a leg into one CBD Leg per FBP Part.
        assertEquals(result.getLegsList(), List.of(MARKET_BASED_LEG, MARKET_BASED_LEG));
        verify(marketBasedLegConverter, times(1)).convert(eq(marketBasedLeg), any(), eq(BET_ID));
        verifyNoInteractions(outcomeBasedLegConverter);
    }

    @Test
    public void shouldAddNoLegsWhenTheLegConverterReturnsNone() {
        LegOuterClass.Leg outcomeBasedLeg = LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.OUTCOME_BASED)
                .build();

        Bet result = convertBetWith(outcomeBasedLeg);

        assertEquals(result.getLegsList().size(), 0);
    }

    @Test
    public void shouldPassTheSuperSubEligibleRampIdsOfTheBetToTheLegConverter() {
        LegOuterClass.Leg marketBasedLeg = LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                .addPart(buildPart(SPORTEX_IDENTIFIER, "111"))
                .build();
        BetOuterClass.Bet.Builder bet = createTestBetBuilder().clearLegs()
                .addLegs(marketBasedLeg)
                .setFeatures(FeaturesOuterClass.Features.newBuilder()
                        .setSuperSub(FeaturesOuterClass.SuperSub.newBuilder()
                                .addAllSelectionRampIds(List.of("ramp-1", "ramp-2"))
                                .build())
                        .build());
        BetDomainEventOuterClass.BetDomainEvent betDomainEvent = BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setBetVersion(StringValue.of("10"))
                .setBet(bet)
                .build();
        prepareMocks(betDomainEvent, null);

        victim.convert(betDomainEvent);

        verify(marketBasedLegConverter, times(1)).convert(marketBasedLeg, Set.of("ramp-1", "ramp-2"), BET_ID);
    }

    @Test
    public void shouldConvertBetMixingOutcomeBasedAndMarketBasedLegs() {
        LegOuterClass.Leg outcomeBasedLeg = LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.OUTCOME_BASED)
                .addPart(buildPart(SPORTEX_IDENTIFIER, "111"))
                .addPart(buildPart(CHOICE_IDENTIFIER, "choice-1"))
                .addPart(buildPart(CHOICE_IDENTIFIER, "choice-2"))
                .build();
        LegOuterClass.Leg marketBasedLeg = LegOuterClass.Leg.newBuilder()
                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                .addPart(buildPart(SPORTEX_IDENTIFIER, "222"))
                .build();

        Bet result = convertBetWith(outcomeBasedLeg, marketBasedLeg);

        // Each leg is routed to its own converter and the converted legs are concatenated in FBP leg order.
        assertEquals(result.getLegsList(), List.of(OUTCOME_BASED_LEG, MARKET_BASED_LEG));
        verify(outcomeBasedLegConverter, times(1)).convert(outcomeBasedLeg, BET_ID);
        verify(marketBasedLegConverter, times(1)).convert(eq(marketBasedLeg), any(), eq(BET_ID));
    }

    /**
     * Mirrors {@link OutcomeBasedLegConverter}: an outcome-based leg becomes the single CBD Leg it is quoted as,
     * or no leg at all when it carries no Parts.
     */
    private static Answer<List<Leg>> singleLegPerFbpLeg() {
        return invocation -> {
            LegOuterClass.Leg leg = invocation.getArgument(0);
            return leg.getPartCount() == 0 ? List.of() : List.of(OUTCOME_BASED_LEG);
        };
    }

    /**
     * Mirrors {@link MarketBasedLegConverter}: a leg of any other type becomes one CBD Leg per FBP Part.
     */
    private static Answer<List<Leg>> oneLegPerFbpPart() {
        return invocation -> {
            LegOuterClass.Leg leg = invocation.getArgument(0);
            return leg.getPartList().stream().map(part -> MARKET_BASED_LEG).collect(Collectors.toList());
        };
    }

    private Bet convertBetWith(LegOuterClass.Leg... legs) {
        BetOuterClass.Bet.Builder bet = createTestBetBuilder().clearLegs();
        for (LegOuterClass.Leg leg : legs) {
            bet.addLegs(leg);
        }

        BetDomainEventOuterClass.BetDomainEvent betDomainEvent = BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setBetVersion(StringValue.of("10"))
                .setBet(bet)
                .build();

        prepareMocks(betDomainEvent, null);

        Bet result = victim.convert(betDomainEvent);
        assertNotNull(result);
        return result;
    }

    private PartOuterClass.Part buildPart(String identifier, String id) {
        return PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(identifier, id)
                                .build())
                        .build())
                .build();
    }

    private void assertConversionResult(Bet result,
                                        BetDomainEventOuterClass.BetDomainEvent betDomainEvent,
                                        boolean expectedIsSameGameMultis,
                                        boolean expectedHasPrice,
                                        SourceTag expectedSourceTag,
                                        boolean expectedHasEarlyWinReward) {
        assertNotNull(result);
        assertEquals(result.getBetQuotingData(), BetQuotingData.newBuilder().getDefaultInstanceForType());
        verify(marketBasedLegConverter, times(2)).convert(any(), any(), any());
        verifyNoInteractions(outcomeBasedLegConverter);
        assertEquals(result.getLegsList().size(), 2);
        assertFalse(result.getIsSettled());
        assertEquals(result.getPotentialWin(), DoubleValue.of(1_000d));
        assertEquals(result.getOriginalPotentialWin(), DoubleValue.of(999d));
        assertEquals(result.getBetReceiptId(), "MOCK_BET_RECEIPT_ID");
        assertEquals(result.getPrice(), Odds.newBuilder().build());
        assertEquals(result.getCurrentWinningsWithoutBogCap(), 1.2d);
        assertEquals(result.getCurrentWinningsExcludingBog(), 0.2d);
        assertEquals(result.getCashedOutAmount(), 2d);
        assertTrue(result.hasHasPriceBoostedChangeInTerms());
        assertTrue(result.getHasPriceBoostedChangeInTerms().getValue());
        assertTrue(result.getIsTeaser());
        assertEquals(result.getCurrency(), "EUR");
        assertEquals(result.getPlatform(), Platform.ONLINE);
        assertEquals(result.getRetailInfo(), RetailInfo.getDefaultInstance());
        assertEquals(result.getFundingType(), FundingType.CASH);
        assertEquals(result.getStatus(), BetStatus.ACTIVE);
        assertEquals(result.getPlacedDate(), Timestamp.getDefaultInstance());
        assertFalse(result.getCashoutPending());
        assertEquals(result.getEligibleJurisdictionsList(), List.of(Jurisdiction.INTERNATIONAL));
        assertEquals(result.getIsSameGameMultis(), expectedIsSameGameMultis);
        if (expectedHasPrice) {
            assertEquals(result.getBetPrice(), 2.5d);
        } else {
            assertFalse(result.hasPrice());
        }
        assertEquals(result.getSourceTag(), expectedSourceTag);
        assertEquals(result.getBetVersion(), StringValue.of("10"));
        verify(betContextEnricherChain, times(1)).enrich(eq(betDomainEvent), any());
        verify(accaFreezeEnricher, times(1)).enrich(any(), any());
        assertEquals(result.getHasEarlyWinReward(), expectedHasEarlyWinReward);
        assertTrue(result.hasRiskContext());
        assertEquals(result.getRiskContext(), EXPECTED_RISK_CONTEXT);
        verify(riskContextConverter, times(1)).convert(betDomainEvent.getRiskContext());
    }

    private void prepareMocks(BetDomainEventOuterClass.BetDomainEvent betDomainEvent,
                              CombinedPriceOuterClass.CombinedPrice combinedPrice) {
        when(betQuotingDataConverter.convert(betDomainEvent.getBet())).thenReturn(BetQuotingData.newBuilder().getDefaultInstanceForType());
        when(outcomeBasedLegConverter.convert(any(), any())).thenAnswer(singleLegPerFbpLeg());
        when(marketBasedLegConverter.convert(any(), any(), any())).thenAnswer(oneLegPerFbpPart());
        when(eligibleJurisdictionConverter.convert(betDomainEvent.getBet())).thenReturn(List.of(Jurisdiction.INTERNATIONAL));
        when(fundingTypeConverter.convert(List.of(WalletInfoOuterClass.WalletInfo.getDefaultInstance()))).thenReturn(FundingType.CASH);
        when(betStatusConverter.convert(BetStatusOuterClass.BetStatus.ACTIVE)).thenReturn(BetStatus.ACTIVE);
        when(javaValueToProtoValueConverter.convertToTimestamp(1681551000)).thenReturn(Timestamp.getDefaultInstance());
        when(oddsConverter.convert(combinedPrice)).thenReturn(Odds.newBuilder().build());
        when(javaValueToProtoValueConverter.convertToDoubleValue(anyDouble()))
                .thenAnswer((Answer<DoubleValue>) invocation -> {
                    double input = invocation.getArgument(0);
                    return DoubleValue.of(input);
                });
        when(riskContextConverter.convert(betDomainEvent.getRiskContext())).thenReturn(Optional.of(EXPECTED_RISK_CONTEXT));
    }

    private BetOuterClass.Bet.Builder createTestBetBuilder() {
        return BetOuterClass.Bet.newBuilder()
                .addLegs(LegOuterClass.Leg.newBuilder().addPart(PartOuterClass.Part.getDefaultInstance()).build())
                .addLegs(LegOuterClass.Leg.newBuilder().addPart(PartOuterClass.Part.getDefaultInstance()).build())
                .setIsSettled(false)
                .setPotentialWinInfo(BetPotentialWinOuterClass.BetPotentialWin.newBuilder()
                        .setCurrentPotentialWin(1_000d)
                        .setMarginatedPotentialWin(999d)
                        .build())
                .setBetId(BET_ID)
                .setBetReceiptId("MOCK_BET_RECEIPT_ID")
                .setStatus(BetStatusOuterClass.BetStatus.ACTIVE)
                .setPlacedDate(1681551000)
                .addAllWalletInfo(List.of(WalletInfoOuterClass.WalletInfo.getDefaultInstance()))
                .setCashoutPending(false)
                .addChangeInTerms(buildChangeInTerms(2d, ChangeInTermsOuterClass.ChangeInTerms.Reason.PARTIAL_CASHOUT))
                .addChangeInTerms(buildChangeInTerms(5d, ChangeInTermsOuterClass.ChangeInTerms.Reason.EDGE_EACHWAY))
                .addChangeInTerms(buildChangeInTerms(ChangeInTermsOuterClass.ChangeInTerms.Reason.BOOST))
                .addChangeInTerms(buildChangeInTerms(ChangeInTermsOuterClass.ChangeInTerms.Reason.TEASER))
                .addChangeInTerms(buildChangeInTerms(ChangeInTermsOuterClass.ChangeInTerms.Reason.LEG_FREEZE))
                .setCurrentWinnings(CurrentWinningsOuterClass.CurrentWinnings.newBuilder()
                        .setCurrentWinningsWithoutBogCap(1.2d)
                        .setCurrentWinningsExcludingBog(0.2d)
                        .build())
                .setCurrency("EUR")
                .setSourceTag(SourceTagOuterClass.SourceTag.OPENBET);
    }

    private ChangeInTermsOuterClass.ChangeInTerms buildChangeInTerms(double cashoutValue, ChangeInTermsOuterClass.ChangeInTerms.Reason reasonCode) {
        return ChangeInTermsOuterClass.ChangeInTerms.newBuilder()
                .setReasonCode(reasonCode)
                .setCashoutValue(DoubleValue.of(cashoutValue))
                .build();
    }

    private ChangeInTermsOuterClass.ChangeInTerms buildChangeInTerms(ChangeInTermsOuterClass.ChangeInTerms.Reason reasonCode) {
        if (reasonCode == null)
            return null;
        return ChangeInTermsOuterClass.ChangeInTerms.newBuilder()
                .setReasonCode(reasonCode)
                .build();
    }

    private CombinedPriceOuterClass.CombinedPrice buildCombinedPrice(CombinedPriceOuterClass.CombinedPrice.CombinedPriceType combinedPriceType) {
        if (combinedPriceType == null)
            return null;
        return CombinedPriceOuterClass.CombinedPrice.newBuilder()
                .setDecimal(2.5d)
                .setType(combinedPriceType)
                .setFractional(FractionOuterClass.Fraction.newBuilder()
                        .setNumerator(3)
                        .setDenominator(2)
                        .build())
                .build();
    }

    private FeaturesOuterClass.Features buildFeatures(boolean hasEarlyWinReward) {
        if (hasEarlyWinReward) {
            return FeaturesOuterClass.Features.newBuilder()
                    .setEarlyWin(FeaturesOuterClass.EarlyWin.newBuilder().build())
                    .build();
        }
        return null;
    }
}