package com.flutter.gbp.betstore.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.FundingType;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUSES;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.DEPOSITS;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.WAGERING;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.CASHOUT;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;
import static org.testng.Assert.assertEquals;

public class FundingTypeConverterTest {

    private FundingTypeConverter victim;

    @BeforeMethod
    public void setUp() {
        victim = new FundingTypeConverter();
    }

    @Test(dataProvider = "fundingTypeTestData")
    public void testConvert(List<WalletInfoOuterClass.WalletInfo> walletInfos, FundingType expectedFundingType) {
        FundingType fundingType = victim.convert(walletInfos);

        assertEquals(fundingType, expectedFundingType);
    }

    @DataProvider(name = "fundingTypeTestData")
    public Object[][] fundingTypeTestData() {
        return new Object[][] {
                // Test case 1: BONUS_CASH wallet
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(BONUS_CASH))
                                        .build()
                        ),
                        FundingType.BONUS_CASH
                },
                // Test case 2: WAGERING wallet
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(WAGERING))
                                        .build()
                        ),
                        FundingType.WAGERING
                },
                // Test case 3: No matching wallet type
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .build()
                        ),
                        FundingType.CASH
                },
                // Test case 4: Multiple wallets with BONUSES and BONUS_CASH
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(BONUSES))
                                        .build()
                        ),
                        FundingType.BONUS_CASH
                },
                // Test case 5: Multiple wallets with BONUSES and BONUS_CASH
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(BONUS_CASH))
                                        .build()
                        ),
                        FundingType.BONUS_CASH
                },
                // Test case 6: Multiple wallets with different types
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(WAGERING))
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(DEPOSITS))
                                        .build()
                        ),
                        FundingType.WAGERING
                },
                // Test case 7: No matching wallet type using other bet operation type
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(CASHOUT)
                                        .build()
                        ),
                        FundingType.CASH
                },
                // Test case 8: No wallet info
                {
                        Collections.emptyList(),
                        FundingType.CASH
                }
        };
    }
}
