package com.flutter.gbp.betstore.writer.convert.streams.fbp.bet;

import com.flutter.gbp.betstore.writer.convert.streams.BetClassConverter;
import com.flutter.gbp.betstore.writer.convert.streams.BetTypeConverter;
import com.flutter.gbp.fcq.bet.BetClass;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetType;
import com.google.protobuf.DoubleValue;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.LegBetTypeOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.edge.BetEdgeOuterClass;
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

    @Mock
    private BetTypeConverter<BetTypeOuterClass.BetType> betTypeConverter;

    @Mock
    private BetClassConverter<LegBetTypeOuterClass.LegBetType> betClassConverter;

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
        when(betTypeConverter.convert(BetTypeOuterClass.BetType.DOUBLE))
                .thenReturn(BetType.DOUBLE);
        when(betClassConverter.convert(LegBetTypeOuterClass.LegBetType.WIN))
                .thenReturn(BetClass.WIN);

        // Perform conversion
        BetQuotingData result = victim.convert(bet);

        // Assertions
        assertNotNull(result);
        assertEquals(result.getBetId(), "123");
        assertEquals(result.getBetType(), BetType.DOUBLE);
        assertEquals(result.getBetClass(), BetClass.WIN);
        assertEquals(result.getNumLines(), 1);
        assertEquals(result.getSize(), 10.0d);
        assertEquals(result.getSizePerLine(), 10.0d);
        assertEquals(result.getCurrentSize(), 5.0d);
        assertEquals(result.getCurrentSizePerLine(), 10.0d);
        assertEquals(result.getNonRedeemableAmount(), 2.0d);
        assertEquals(result.getStakeFactor(), 1.1d);
        assertEquals(result.getAccaInsuranceFactor(), 0.2d);
        assertEquals(result.getBogWinningsCap(), DoubleValue.of(1000d));
    }

    private BetOuterClass.Bet createSampleBet() {
        return BetOuterClass.Bet.newBuilder()
                .setBetId("123")
                .setBetType(BetTypeOuterClass.BetType.DOUBLE)
                .setLegBetType(LegBetTypeOuterClass.LegBetType.WIN)
                .setNumLines(1)
                .setStake(10.0d)
                .setCurrentStake(5.0d)
                .setStakePerLine(10.0d)
                .setCurrentStakePerLine(10.0d)
                .addAllWalletInfo(List.of(WalletInfoOuterClass.WalletInfo.newBuilder()
                        .setBetOperationType(WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT)
                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder()
                                .setWalletType(WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH)
                                .setNonRedeemableAmount(2.0d)
                                .build())
                        .build()))
                .setEdge(BetEdgeOuterClass.BetEdge.newBuilder()
                        .setBetEdgeType(BetEdgeOuterClass.BetEdge.BetEdgeType.ACCA_INSURANCE)
                        .setStatus(BetEdgeOuterClass.BetEdge.BetEdgeStatus.ACTIVE)
                        .setFactor(DoubleValue.of(0.2d))
                        .build())
                .setStakeFactor(1.1d)
                .setBogWinningsCap(DoubleValue.of(1000d))
                .build();
    }
}