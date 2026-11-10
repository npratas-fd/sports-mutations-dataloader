package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.BetClassConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.BetTypeConverter;
import com.flutter.gbp.fcq.bet.BetClass;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetType;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.BetTypeOuterClass;
import com.ppb.platform.sb.retail.proto.bet.LegBetTypeOuterClass;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;


@Mapper(implementationName = "BetQuotingDataConverterImpl", componentModel = "spring")
public abstract class BetQuotingDataConverter {

    private static final String DEFAULT_STAKE_FACTOR = "0.0";
    private static final String NO_ACCA_EDGE = "1.0";
    @Resource(name = "abpBetTypeConverter")
    private BetTypeConverter<BetTypeOuterClass.BetType> betTypeConverter;
    @Resource(name = "abpBetClassConverter")
    private BetClassConverter<LegBetTypeOuterClass.LegBetType> betClassConverter;

    @Mapping(target = "betId", source = "bet.betId")
    @Mapping(target = "betType", source = "bet.betType", qualifiedByName = "convertBetType")
    @Mapping(target = "betClass", source = "bet.legBetType", qualifiedByName = "convertBetClass")
    @Mapping(target = "numLines", source = "bet.numLinesInfo.numLines.value")
    @Mapping(target = "size", source = "bet.stake")
    @Mapping(target = "currentSize", source = "bet", qualifiedByName = "convertCurrentSize")
    @Mapping(target = "sizePerLine", source = "bet.stakePerLine.value")
    @Mapping(target = "currentSizePerLine", source = "bet.currentStakePerLine.value")
    @Mapping(target = "nonRedeemableAmount", source = "bet.walletInfoList", qualifiedByName = "convertNonRedeemableAmount")
    @Mapping(target = "stakeFactor", constant = DEFAULT_STAKE_FACTOR)
    @Mapping(target = "accaInsuranceFactor", constant = NO_ACCA_EDGE)
    @Mapping(target = "bogWinningsCap", ignore = true)
    @Mapping(target = "betIdBytes", ignore = true)
    @Mapping(target = "betTypeValue", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract BetQuotingData convert(BetOuterClass.Bet bet);

    @Named("convertBetType")
    protected BetType convertBetType(BetTypeOuterClass.BetType betType) {
        return betTypeConverter.convert(betType);
    }

    @Named("convertBetClass")
    protected BetClass convertBetClass(LegBetTypeOuterClass.LegBetType legBetType) {
        return betClassConverter.convert(legBetType);
    }

    @Named("convertCurrentSize")
    protected double convertCurrentSize(BetOuterClass.Bet bet) {
        if (bet.hasCurrentStake()) {
            return bet.getCurrentStake().getValue();
        }
        return bet.getStake();
    }

    @Named("convertNonRedeemableAmount")
    protected double convertNonRedeemableAmount(List<WalletInfoOuterClass.WalletInfo> walletInfoList) {
        return walletInfoList.stream()
                .filter(Objects::nonNull)
                .filter(walletInfo -> walletInfo.getBetOperationType() == PLACEMENT)
                .flatMap(walletInfo -> walletInfo.getWalletList().stream())
                .filter(Objects::nonNull)
                .filter(wallet -> wallet.getWalletType() == BONUS_CASH)
                .mapToDouble(WalletInfoOuterClass.Wallet::getNonRedeemableAmount)
                .sum();
    }
}
