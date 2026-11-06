package com.flutter.gbp.betstore.writer.convert.streams.abp.bet;

import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.EligibleJurisdictionConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.FundingTypeConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.OddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.SgmEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetStatus;
import com.flutter.gbp.fcq.bet.Channel;
import com.flutter.gbp.fcq.bet.FundingType;
import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.flutter.gbp.fcq.bet.Platform;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.retail.proto.bet.CurrentWinningsOuterClass;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import com.ppb.platform.sb.retail.proto.bet.status.BetStatusOuterClass;
import com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class BetConverterTest {

    @Mock
    private BetQuotingDataConverter betQuotingDataConverter;
    @Mock
    private LegConverter legMapper;
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
    private SgmEnricher sgmEnricher;

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

    @Test(dataProvider = "dataProviderForAbpBetStream")
    public void shouldConvertAbpBetStreamCorrectly(CombinedPriceOuterClass.CombinedPrice.CombinedPriceType combinedPriceType,
                                                boolean expectedIsSameGameMultis) {
        CombinedPriceOuterClass.CombinedPrice combinedPrice = buildCombinedPrice(combinedPriceType);
        BetOuterClass.Bet.Builder bet = createTestBetBuilder();
        if (combinedPriceType != null)
            bet.setCombinedPrice(combinedPrice);
        BetDomainEventOuterClass.BetDomainEvent betDomainEvent = BetDomainEventOuterClass.BetDomainEvent.newBuilder()
                .setBet(bet)
                .build();
        // Mock the behavior of dependent components
        prepareMocks(betDomainEvent);
        // conversion
        Bet result = victim.convert(betDomainEvent);
        // Assertions
        assertConversionResult(result, betDomainEvent, expectedIsSameGameMultis, combinedPriceType != null);
    }

    @DataProvider
    public static Object[][] dataProviderForAbpBetStream() {
        return new Object[][]{
                {CombinedPriceOuterClass.CombinedPrice.CombinedPriceType.REGULAR, false},
                {CombinedPriceOuterClass.CombinedPrice.CombinedPriceType.SGM, true},
                {null, false}

        };
    }

    private void assertConversionResult(Bet result,
                                        BetDomainEventOuterClass.BetDomainEvent betDomainEvent,
                                        boolean expectedIsSameGameMultis, boolean expectedHasPrice) {
        assertNotNull(result);
        assertEquals(result.getBetQuotingData(), BetQuotingData.newBuilder().getDefaultInstanceForType());
        verify(legMapper, times(2)).convert(any(), any());
        assertEquals(result.getLegsList().size(), 2);
        assertFalse(result.getIsSettled());
        assertEquals(result.getPotentialWin(), DoubleValue.of(1_000d));
        assertEquals(result.getOriginalPotentialWin(), DoubleValue.of(1_000d));
        assertEquals(result.getBetReceiptId(), "MOCK_BET_RECEIPT_ID");
        assertEquals(result.getCurrentWinningsWithoutBogCap(), 1.2d);
        assertEquals(result.getCurrentWinningsExcludingBog(), 1.2d);
        assertEquals(result.getPlatform(), Platform.RETAIL);
        assertEquals(result.getRetailInfo().getTicketSize(), 1);
        assertEquals(result.getRetailInfo().getChannel(), Channel.SSBT);
        assertEquals(result.getPlacedDate(), Timestamp.getDefaultInstance());
        assertEquals(result.getPrice(), Odds.newBuilder().build());
        assertEquals(result.getEligibleJurisdictionsList(), List.of(Jurisdiction.INTERNATIONAL));
        assertEquals(result.getFundingType(), FundingType.CASH);
        assertEquals(result.getStatus(), BetStatus.ACTIVE);
        assertTrue(result.hasHasPriceBoostedChangeInTerms());
        assertFalse(result.getHasPriceBoostedChangeInTerms().getValue());
        assertEquals(result.getIsSameGameMultis(), expectedIsSameGameMultis);
        if (expectedHasPrice) {
            assertEquals(result.getBetPrice(), 2.5d);
        } else {
            assertFalse(result.hasPrice());
        }
        verify(sgmEnricher, times(1)).enrichWithSgmData(eq(betDomainEvent.getBet()), any());
    }

    private void prepareMocks(BetDomainEventOuterClass.BetDomainEvent betDomainEvent) {
        when(betQuotingDataConverter.convert(betDomainEvent.getBet())).thenReturn(BetQuotingData.newBuilder().getDefaultInstanceForType());
        when(legMapper.convert(any(), any())).thenReturn(Leg.newBuilder().build());
        when(eligibleJurisdictionConverter.convert(betDomainEvent.getBet())).thenReturn(List.of(Jurisdiction.INTERNATIONAL));
        when(fundingTypeConverter.convert(List.of(WalletInfoOuterClass.WalletInfo.getDefaultInstance()))).thenReturn(FundingType.CASH);
        when(betStatusConverter.convert(BetStatusOuterClass.BetStatus.ACTIVE)).thenReturn(BetStatus.ACTIVE);
        when(javaValueToProtoValueConverter.convertToTimestamp(1681551000L)).thenReturn(Timestamp.getDefaultInstance());
        when(oddsConverter.convert(2.5)).thenReturn(Odds.newBuilder().build());
    }

    private BetOuterClass.Bet.Builder createTestBetBuilder() {
        return BetOuterClass.Bet.newBuilder()
                .addLegs(LegOuterClass.Leg.newBuilder().addPart(PartOuterClass.Part.getDefaultInstance()).build())
                .addLegs(LegOuterClass.Leg.newBuilder().addPart(PartOuterClass.Part.getDefaultInstance()).build())
                .setIsSettled(false)
                .setPotentialWin(DoubleValue.of(1_000d))
                .setBetReceiptId("MOCK_BET_RECEIPT_ID")
                .setStatus(BetStatusOuterClass.BetStatus.ACTIVE)
                .setPlacedDate(1681551000)
                .setCurrentWinnings(CurrentWinningsOuterClass.CurrentWinnings.newBuilder()
                        .setAmount(1.2d)
                        .build())
                .addAllWalletInfo(List.of(WalletInfoOuterClass.WalletInfo.getDefaultInstance()))
                .setTicketSize(1)
                .setChannel("ssbt");
    }

    private CombinedPriceOuterClass.CombinedPrice buildCombinedPrice(CombinedPriceOuterClass.CombinedPrice.CombinedPriceType combinedPriceType) {
        if (combinedPriceType == null)
            return null;
        return CombinedPriceOuterClass.CombinedPrice.newBuilder()
                .setDecimal(2.5d)
                .setType(combinedPriceType)
                .build();
    }

}