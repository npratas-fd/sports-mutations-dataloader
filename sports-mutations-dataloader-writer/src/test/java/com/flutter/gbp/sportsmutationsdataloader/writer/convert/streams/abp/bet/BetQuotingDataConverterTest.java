package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.BetClassConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.BetTypeConverter;
import com.flutter.gbp.fcq.bet.BetClass;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetType;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass;
import com.ppb.platform.sb.retail.proto.bet.LegBetTypeOuterClass;
import com.ppb.platform.sb.retail.proto.bet.NumLinesInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class BetQuotingDataConverterTest {

    private final double DEFAULT_STAKE_FACTOR = 0.0d;
    private final double DEFAULT_DOUBLE_VALUE = 0.0d;
    private final double NO_ACCA_EDGE = 1.0d;

    @Mock
    private BetTypeConverter<BetTypeOuterClass.BetType> abpBetTypeConverter;

    @Mock
    private BetClassConverter<LegBetTypeOuterClass.LegBetType> abpBetClassConverter;

    @InjectMocks
    private BetQuotingDataConverterImpl victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void testConvert() {
        // Prepare test data
        BetOuterClass.Bet bet = createSampleBet();

        // Mock behavior of converters
        when(abpBetTypeConverter.convert(BetTypeOuterClass.BetType.DOUBLE))
                .thenReturn(BetType.DOUBLE);
        when(abpBetClassConverter.convert(LegBetTypeOuterClass.LegBetType.WIN))
                .thenReturn(BetClass.WIN);

        // Perform conversion
        BetQuotingData result = victim.convert(bet);

        // Assertions
        assertNotNull(result);
        assertEquals(result.getBetId(), "123");
        assertEquals(result.getBetType(), BetType.DOUBLE);
        assertEquals(result.getBetClass(), BetClass.WIN);
        assertEquals(result.getNumLines(), 1);
        assertEquals(result.getSize(), 10.0);
        assertEquals(result.getSizePerLine(), 10.0);
        assertEquals(result.getCurrentSize(), 5.0);
        assertEquals(result.getCurrentSizePerLine(), 10.0);
        assertEquals(result.getNonRedeemableAmount(), 2.0d);
        assertEquals(result.getStakeFactor(), DEFAULT_STAKE_FACTOR);
        assertEquals(result.getAccaInsuranceFactor(), NO_ACCA_EDGE);
        assertEquals(result.getBogWinningsCap(), DoubleValue.of(DEFAULT_DOUBLE_VALUE));
    }

    private BetOuterClass.Bet createSampleBet() {
        return BetOuterClass.Bet.newBuilder()
                .setBetId("123")
                .setBetType(BetTypeOuterClass.BetType.DOUBLE)
                .setLegBetType(LegBetTypeOuterClass.LegBetType.WIN)
                .setNumLinesInfo(NumLinesInfoOuterClass.NumLinesInfo.newBuilder().setNumLines(Int32Value.of(1)).build())
                .setStake(10.0d)
                .setCurrentStake(DoubleValue.of(5.0d))
                .setStakePerLine(DoubleValue.of(10.0d))
                .setCurrentStakePerLine(DoubleValue.of(10.0d))
                .addAllWalletInfo(List.of(WalletInfoOuterClass.WalletInfo.newBuilder()
                        .setBetOperationType(WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT)
                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder()
                                .setWalletType(WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH)
                                .setNonRedeemableAmount(2.0d)
                                .build())
                        .build()))
                .build();
    }
}