package com.flutter.gbp.betstore.writer.convert.streams.fbp;

import com.flutter.gbp.betstore.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import static com.ppb.platform.sb.fbp.proto.bet.BetGroupTypeOuterClass.BetGroupType.SYSTEM_BET;
import static com.ppb.platform.sb.fbp.proto.bet.PromotionTypeOuterClass.PromotionType.ENHANCED_PRICE;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUSES;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.WAGERING;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;

public class EligibleJurisdictionConverter implements Converter<BetOuterClass.Bet, List<Jurisdiction>> {
    @Override
    public List<Jurisdiction> convert(BetOuterClass.Bet source) {
        List<Jurisdiction> jurisdictions = new ArrayList<>();

        if (isInternationalEligible(source)) {
            jurisdictions.add(Jurisdiction.INTERNATIONAL);
        }

        if (isItalyEligible(source)) {
            jurisdictions.add(Jurisdiction.ITALY);
        }

        return jurisdictions;
    }


    private boolean isInternationalEligible(BetOuterClass.Bet bet) {
        if (bet.getPromotion() == ENHANCED_PRICE) {
            return false;
        }

        return bet.getLegsList().stream()
                .flatMap(leg -> leg.getPartList().stream())
                .noneMatch(PartOuterClass.Part::getIsBanker);
    }


    private boolean isItalyEligible(BetOuterClass.Bet bet) {
        if (bet.getBetGroup() == SYSTEM_BET) {
            return false;
        }

        return bet.getWalletInfoList().stream()
                .filter(Objects::nonNull)
                .filter(walletInfo -> walletInfo.getBetOperationType() == PLACEMENT)
                .noneMatch(this::hasIneligibleWalletTypes);
    }

    private boolean hasIneligibleWalletTypes(WalletInfoOuterClass.WalletInfo walletInfo) {
        EnumSet<WalletInfoOuterClass.Wallet.WalletType> ineligibleTypes =
                EnumSet.of(BONUSES, BONUS_CASH, WAGERING);

        return walletInfo.getWalletList().stream()
                .map(WalletInfoOuterClass.Wallet::getWalletType)
                .anyMatch(ineligibleTypes::contains);
    }
}
