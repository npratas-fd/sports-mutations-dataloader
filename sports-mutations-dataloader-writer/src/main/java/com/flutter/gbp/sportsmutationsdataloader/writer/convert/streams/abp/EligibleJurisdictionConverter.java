package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;


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
        return bet.getLegsList().stream()
                .flatMap(leg -> leg.getPartList().stream())
                .noneMatch(PartOuterClass.Part::getIsBanker);
    }

    private boolean isItalyEligible(BetOuterClass.Bet bet) {
        return bet.getWalletInfoList().stream()
                .filter(walletInfo -> walletInfo.getBetOperationType() == PLACEMENT)
                .noneMatch(this::hasIneligibleWalletTypes);
    }

    private boolean hasIneligibleWalletTypes(WalletInfoOuterClass.WalletInfo walletInfo) {
        EnumSet<WalletInfoOuterClass.Wallet.WalletType> ineligibleTypes =
                EnumSet.of(BONUS_CASH);

        return walletInfo.getWalletList().stream()
                .map(WalletInfoOuterClass.Wallet::getWalletType)
                .anyMatch(ineligibleTypes::contains);
    }
}
