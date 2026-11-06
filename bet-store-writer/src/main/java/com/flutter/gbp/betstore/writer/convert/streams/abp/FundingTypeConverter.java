package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.betstore.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.FundingType;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;

import java.util.List;
import java.util.Objects;

import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;


public class FundingTypeConverter implements Converter<List<WalletInfoOuterClass.WalletInfo>, FundingType> {
    @Override
    public FundingType convert(List<WalletInfoOuterClass.WalletInfo> source) {
        // If no walletInfo is provided or is not Bonus, returns CASH as default
        if (source == null || isNotBonus(source)) {
            return FundingType.CASH;
        }

        return FundingType.BONUS_CASH;
    }

    private boolean isNotBonus(List<WalletInfoOuterClass.WalletInfo> source) {
        return source.stream()
                .filter(Objects::nonNull)
                .filter(walletInfo -> walletInfo.getBetOperationType() == PLACEMENT)
                .flatMap(walletInfo -> walletInfo.getWalletList().stream())
                .filter(Objects::nonNull)
                .map(WalletInfoOuterClass.Wallet::getWalletType)
                .noneMatch(walletType -> walletType.equals(BONUS_CASH));
    }
}
