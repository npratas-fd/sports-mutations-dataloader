package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class EligibleJurisdictionConverterTest {
    private EligibleJurisdictionConverter victim;

    @BeforeMethod
    public void setUp() {
        victim = new EligibleJurisdictionConverter();
    }

    @Test
    public void testConvert() {
        // Create a sample BetOuterClass.Bet instance
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .build();

        // Call the converter
        List<Jurisdiction> jurisdictions = victim.convert(bet);

        // Assertions
        assertNotNull(jurisdictions);
        assertEquals(jurisdictions.size(), 2);
        assertEquals(jurisdictions.get(0), Jurisdiction.INTERNATIONAL);
        assertEquals(jurisdictions.get(1), Jurisdiction.ITALY);
    }

    @Test
    public void testConvertWithoutItalyEligible() {
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .addWalletInfo(WalletInfoOuterClass.WalletInfo.newBuilder()
                        .setBetOperationType(WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT)
                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder()
                                .setWalletType(WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH)
                                .build())
                        .build())
                .build();

        // Call the converter
        List<Jurisdiction> jurisdictions = victim.convert(bet);

        // Assertions
        assertNotNull(jurisdictions);
        assertEquals(jurisdictions.size(), 1);
        assertEquals(jurisdictions.get(0), Jurisdiction.INTERNATIONAL);
    }

    @Test
    public void testConvertWithABankerLeg() {
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .addWalletInfo(WalletInfoOuterClass.WalletInfo.newBuilder()
                        .setBetOperationType(WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT)
                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder()
                                .setWalletType(WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH)
                                .build())
                        .build())
                .addLegs(
                        LegOuterClass.Leg.newBuilder()
                                .addPart(PartOuterClass.Part.newBuilder()
                                        .setIsBanker(true)
                                        .build())
                                .build()
                )
                .build();

        // Call the converter
        List<Jurisdiction> jurisdictions = victim.convert(bet);

        // Assertions
        assertNotNull(jurisdictions);
        assertEquals(jurisdictions.size(), 0);
    }
}