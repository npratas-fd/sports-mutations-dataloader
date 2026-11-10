package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.bet;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.BetClassConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.BetTypeConverter;
import com.flutter.gbp.fcq.bet.BetClass;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetType;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.BetTypeOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.LegBetTypeOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.Wallet.WalletType.BONUS_CASH;
import static com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass.WalletInfo.BetOperationType.PLACEMENT;
import static com.ppb.platform.sb.fbp.proto.bet.edge.BetEdgeOuterClass.BetEdge.BetEdgeStatus.ACTIVE;
import static com.ppb.platform.sb.fbp.proto.bet.edge.BetEdgeOuterClass.BetEdge.BetEdgeType.ACCA_INSURANCE;

@Mapper(implementationName = "BetQuotingDataConverterImpl", componentModel = "spring")
public abstract class BetQuotingDataConverter {

    private static final double NO_ACCA_EDGE = 1.0d;

    @Resource(name = "fbpBetTypeConverter")
    private BetTypeConverter<BetTypeOuterClass.BetType> betTypeConverter;
    @Resource(name = "fbpBetClassConverter")
    private BetClassConverter<LegBetTypeOuterClass.LegBetType> betClassConverter;

    @Mapping(target = "betId", source = "bet.betId")
    @Mapping(target = "betType", source = "bet.betType", qualifiedByName = "convertBetType")
    @Mapping(target = "betClass", source = "bet.legBetType", qualifiedByName = "convertBetClass")
    @Mapping(target = "numLines", source = "bet.numLines")
    @Mapping(target = "size", source = "bet.stake")
    @Mapping(target = "currentSize", source = "bet.currentStake")
    @Mapping(target = "sizePerLine", source = "bet.stakePerLine")
    @Mapping(target = "currentSizePerLine", source = "bet.currentStakePerLine")
    @Mapping(target = "nonRedeemableAmount", source = "bet.walletInfoList", qualifiedByName = "convertNonRedeemableAmount")
    @Mapping(target = "accaInsuranceFactor", source = "bet", qualifiedByName = "convertAccaInsuranceFactor")
    @Mapping(target = "stakeFactor", source = "bet.stakeFactor")
    @Mapping(target = "bogWinningsCap", source = "bet.bogWinningsCap")
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

    @Named("convertAccaInsuranceFactor")
    protected double convertAccaInsuranceFactor(BetOuterClass.Bet bet) {
        return Optional.ofNullable(bet)
                .filter(BetOuterClass.Bet::hasEdge)
                .map(BetOuterClass.Bet::getEdge)
                .filter(betEdgeToFilter -> betEdgeToFilter.getBetEdgeType() == ACCA_INSURANCE &&
                        betEdgeToFilter.getStatus() == ACTIVE)
                .map(betEdgeFiltered -> betEdgeFiltered.getFactor().getValue())
                .orElse(NO_ACCA_EDGE);
    }
}
