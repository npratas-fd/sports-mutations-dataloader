package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.FundingType;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUSES;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.WAGERING;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;

public class FundingTypeConverter implements Converter<List<WalletInfoOuterClass.WalletInfo>, FundingType> {
    @Override
    public FundingType convert(List<WalletInfoOuterClass.WalletInfo> source) {
        // If no walletInfo is provided, returns CASH as default
        if (source == null) {
            return FundingType.CASH;
        }

        // Filter out wallets that are not relevant and get the set of wallet types
        Set<WalletInfoOuterClass.Wallet.WalletType> walletTypes = source.stream()
                .filter(Objects::nonNull)
                .filter(walletInfo -> walletInfo.getBetOperationType() == PLACEMENT)
                .flatMap(walletInfo -> walletInfo.getWalletList().stream())
                .filter(Objects::nonNull)
                .map(WalletInfoOuterClass.Wallet::getWalletType)
                .collect(Collectors.toSet());

        if (walletTypes.contains(BONUS_CASH) || walletTypes.contains(BONUSES)) {
            return FundingType.BONUS_CASH;
        } else if (walletTypes.contains(WAGERING)) {
            return FundingType.WAGERING;
        }

        return FundingType.CASH;
    }
}
