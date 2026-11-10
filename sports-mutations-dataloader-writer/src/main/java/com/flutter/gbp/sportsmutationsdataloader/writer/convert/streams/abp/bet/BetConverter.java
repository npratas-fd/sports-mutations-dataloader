package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.EligibleJurisdictionConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.FundingTypeConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.OddsConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.SgmEnricher;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.leg.LegConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetStatus;
import com.flutter.gbp.fcq.bet.Channel;
import com.flutter.gbp.fcq.bet.FundingType;
import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.status.BetStatusOuterClass;
import com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.ppb.platform.sb.retail.proto.bet.CombinedPriceOuterClass.CombinedPrice.CombinedPriceType.SGM;
import static org.mapstruct.NullValueCheckStrategy.ALWAYS;


@Mapper(implementationName = "BetConverterImpl", componentModel = "spring", nullValueCheckStrategy = ALWAYS)
public abstract class BetConverter {
    protected static final BoolValue FALSE_BOOL_VALUE = BoolValue.of(false);

    @Resource(name = "abpBetQuotingDataConverter")
    private BetQuotingDataConverter betQuotingDataConverter;
    @Resource(name = "abpLegConverter")
    private LegConverter legConverter;
    @Resource(name = "javaValueToProtoValueConverter")
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Resource(name = "abpOddsConverter")
    private OddsConverter oddsConverter;
    @Resource(name = "abpBetStatusConverter")
    private BetStatusConverter betStatusConverter;
    @Resource(name = "abpEligibleJurisdictionConverter")
    private EligibleJurisdictionConverter eligibleJurisdictionConverter;
    @Resource(name = "abpFundingTypeConverter")
    private FundingTypeConverter fundingTypeConverter;
    @Resource(name = "abpSgmEnricher")
    private SgmEnricher sgmEnricher;

    @Mapping(target = "betQuotingData", source = "bet.bet", qualifiedByName = "convertBetQuotingData")
    @Mapping(target = "isSettled", source = "bet.bet.isSettled")
    @Mapping(target = "fundingType", source = "bet.bet.walletInfoList", qualifiedByName = "convertFundingType")
    @Mapping(target = "potentialWin", source = "bet.bet.potentialWin")
    @Mapping(target = "originalPotentialWin", source = "bet.bet.potentialWin")
    @Mapping(target = "betReceiptId", source = "bet.bet.betReceiptId")
    @Mapping(target = "status", source = "bet.bet.status", qualifiedByName = "convertBetStatus")
    @Mapping(target = "placedDate", source = "bet.bet.placedDate", qualifiedByName = "convertPlacedDate")
    @Mapping(target = "betPrice", source = "bet.bet.combinedPrice.decimal")
    @Mapping(target = "price", source = "bet.bet.combinedPrice.decimal", qualifiedByName = "convertFractionToOdds")
    @Mapping(target = "currentWinningsWithoutBogCap", source = "bet.bet.currentWinnings.amount")
    @Mapping(target = "currentWinningsExcludingBog", source = "bet.bet.currentWinnings.amount")
    @Mapping(target = "platform", constant = "RETAIL")
    @Mapping(target = "retailInfo.ticketSize", source = "bet.ticketSize")
    @Mapping(target = "retailInfo.channel", source = "bet.channel", qualifiedByName = "convertRetailInfoChannel")
    @Mapping(target = "cashedOutAmount", ignore = true)
    @Mapping(target = "cashoutPending", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "isTeaser", ignore = true)
    @Mapping(target = "hasPriceBoostedChangeInTerms", expression = "java(FALSE_BOOL_VALUE)")
    @Mapping(target = "isSameGameMultis", source = "bet.bet", qualifiedByName = "convertIsSameGameMultis")
    @Mapping(target = "retailInfo.unknownFields", ignore = true)
    @Mapping(target = "retailInfo.allFields", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract Bet convert(BetDomainEventOuterClass.BetDomainEvent bet);

    @Named("convertBetQuotingData")
    protected BetQuotingData convertBetQuotingData(BetOuterClass.Bet bet) {
        return betQuotingDataConverter.convert(bet);
    }

    @Named("convertFundingType")
    protected FundingType convertFundingType(List<WalletInfoOuterClass.WalletInfo> walletInfoList) {
        return fundingTypeConverter.convert(walletInfoList);
    }

    @Named("convertPlacedDate")
    protected Timestamp convertPlacedDate(long placedDate) {
        return javaValueToProtoValueConverter.convertToTimestamp(placedDate);
    }

    @Named("convertBetStatus")
    protected BetStatus convertBetStatus(BetStatusOuterClass.BetStatus betStatus) {
        return betStatusConverter.convert(betStatus);
    }

    @Named("convertFractionToOdds")
    protected Odds convertFractionToOdds(double decimalPrice) {
        return oddsConverter.convert(decimalPrice);
    }

    @Named("convertRetailInfoChannel")
    protected Channel convertRetailInfoChannel(String channel) {
        if (channel != null) {
            return Enum.valueOf(Channel.class, channel.toUpperCase());
        }
        return Channel.UNKNOWN_CHANNEL;
    }

    @Named("convertIsSameGameMultis")
    protected boolean convertIsSameGameMultis(BetOuterClass.Bet bet) {
        return bet.hasCombinedPrice() && SGM.equals(bet.getCombinedPrice().getType());
    }

    @AfterMapping
    protected void convertEligibleJurisdictions(BetDomainEventOuterClass.BetDomainEvent source, @MappingTarget Bet.Builder target) {
        if (source.hasBet()) {
            List<Jurisdiction> convertedJurisdictions = eligibleJurisdictionConverter.convert(source.getBet());
            target.addAllEligibleJurisdictions(convertedJurisdictions);
        }
    }

    @AfterMapping
    protected void convertLegs(BetDomainEventOuterClass.BetDomainEvent source, @MappingTarget Bet.Builder target) {
        if (source.hasBet()) {
            List<Leg> convertedLegs = convertLegs(source.getBet());
            target.addAllLegs(convertedLegs);
        }
    }

    @AfterMapping
    protected void enrichSgmData(BetDomainEventOuterClass.BetDomainEvent source, @MappingTarget Bet.Builder bet) {
        if (source.hasBet()) {
            sgmEnricher.enrichWithSgmData(source.getBet(), bet);
        }
    }

    private List<Leg> convertLegs(BetOuterClass.Bet bet) {
        return bet.getLegsList().stream()
                .flatMap(leg -> leg.getPartList().stream()
                        .map(part -> legConverter.convert(part, leg)))
                .collect(Collectors.toList());
    }
}
