package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static com.ppb.platform.sb.fbp.proto.bet.BetGroupTypeOuterClass.BetGroupType.SYSTEM_BET;
import static com.ppb.platform.sb.fbp.proto.bet.PromotionTypeOuterClass.PromotionType.ENHANCED_PRICE;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUSES;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.DEPOSITS;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.WAGERING;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;
import static org.testng.Assert.assertEquals;

public class EligibleJurisdictionConverterTest {
    private EligibleJurisdictionConverter victim;

    @BeforeMethod
    public void setUp() {
        victim = new EligibleJurisdictionConverter();
    }

    @Test
    public void testConvertInternationalAndItalyEligible() {
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .addLegs(LegOuterClass.Leg.newBuilder()
                        .addPart(PartOuterClass.Part.newBuilder().setIsBanker(false))
                        .build())
                .build();

        List<Jurisdiction> jurisdictions = victim.convert(bet);

        assertEquals(jurisdictions.size(), 2);
        assertEquals(jurisdictions.get(0), Jurisdiction.INTERNATIONAL);
        assertEquals(jurisdictions.get(1), Jurisdiction.ITALY);
    }

    @Test
    public void testConvertInternationalNotEligible() {
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setPromotion(ENHANCED_PRICE)
                .addLegs(LegOuterClass.Leg.newBuilder()
                        .addPart(PartOuterClass.Part.newBuilder().setIsBanker(false))
                        .build())
                .build();

        List<Jurisdiction> jurisdictions = victim.convert(bet);

        assertEquals(jurisdictions.size(), 1);
        assertEquals(jurisdictions.get(0), Jurisdiction.ITALY);
    }

    @Test
    public void testConvertItalyEligible() {
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setPromotion(ENHANCED_PRICE)
                .addWalletInfo(WalletInfoOuterClass.WalletInfo.newBuilder()
                        .setBetOperationType(PLACEMENT)
                        .addWallet(WalletInfoOuterClass.Wallet.newBuilder().setWalletType(DEPOSITS))
                        .build())
                .build();

        List<Jurisdiction> jurisdictions = victim.convert(bet);

        assertEquals(jurisdictions.size(), 1);
        assertEquals(jurisdictions.get(0), Jurisdiction.ITALY);
    }

    @Test
    public void testConvertItalyNotEligible() {
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setBetGroup(SYSTEM_BET)
                .addLegs(LegOuterClass.Leg.newBuilder()
                        .addPart(PartOuterClass.Part.newBuilder().setIsBanker(false))
                        .build())
                .build();

        List<Jurisdiction> jurisdictions = victim.convert(bet);

        assertEquals(jurisdictions.size(), 1);
        assertEquals(jurisdictions.get(0), Jurisdiction.INTERNATIONAL);
    }

    @Test(dataProvider = "invalidWalletTypeDataProvider")
    public void testConvertNotEligible(WalletInfoOuterClass.Wallet.WalletType walletType) {
        WalletInfoOuterClass.Wallet wallet = WalletInfoOuterClass.Wallet.newBuilder()
                .setWalletType(walletType)
                .build();
        WalletInfoOuterClass.WalletInfo walletInfo = WalletInfoOuterClass.WalletInfo.newBuilder()
                .setBetOperationType(PLACEMENT)
                .addWallet(wallet)
                .build();

        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setPromotion(ENHANCED_PRICE)
                .addLegs(LegOuterClass.Leg.newBuilder()
                        .addPart(PartOuterClass.Part.newBuilder().setIsBanker(true))
                        .build())
                .addWalletInfo(walletInfo)
                .build();

        List<Jurisdiction> jurisdictions = victim.convert(bet);

        assertEquals(jurisdictions.size(), 0);
    }

    @DataProvider(name = "invalidWalletTypeDataProvider")
    public Object[][] walletTypeData() {
        return new Object[][] {
                { BONUSES },
                { BONUS_CASH },
                { WAGERING }
        };
    }
}