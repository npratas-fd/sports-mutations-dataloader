package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.fcq.bet.FundingType;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.Wallet.WalletType.DEPOSITS;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.Wallet.WalletType.UNKNOWN_WALLET_TYPE;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.SETTLEMENT;
import static org.testng.Assert.assertEquals;

public class FundingTypeConverterTest {

    private FundingTypeConverter converter;

    @BeforeMethod
    public void setUp() {
        converter = new FundingTypeConverter();
    }
    @Test(dataProvider = "fundingTypeTestData")
    public void testConvert(List<WalletInfoOuterClass.WalletInfo> walletInfos, FundingType expectedFundingType) {
        FundingType fundingType = converter.convert(walletInfos);

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
                // Test case 2: CASH wallet
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(DEPOSITS))
                                        .build()
                        ),
                        FundingType.CASH
                },
                // Test case 3: Two wallets, return the first that matches
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(BONUS_CASH))
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(DEPOSITS))
                                        .build()
                        ),
                        FundingType.BONUS_CASH
                },
                // Test case 4: No matching wallet type using other bet operation type
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(SETTLEMENT)
                                        .build()
                        ),
                        FundingType.CASH
                },
                // Test case 5: No matching wallet, UNKNOWN_WALLET_TYPE
                {
                        Collections.singletonList(
                                WalletInfoOuterClass.WalletInfo.newBuilder()
                                        .setBetOperationType(PLACEMENT)
                                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(UNKNOWN_WALLET_TYPE))
                                        .build()
                        ),
                        FundingType.CASH
                },
                // Test case 6: No wallet info
                {
                        Collections.emptyList(),
                        FundingType.CASH
                }
        };
    }
}