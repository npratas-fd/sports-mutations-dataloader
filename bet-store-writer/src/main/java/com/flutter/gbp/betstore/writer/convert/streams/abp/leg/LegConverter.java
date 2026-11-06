package com.flutter.gbp.betstore.writer.convert.streams.abp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.abp.FractionalOddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.OddsConverter;
import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegType;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.retail.proto.bet.EntityOuterClass;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Optional;

@Mapper(implementationName = "LegConverterImpl", componentModel = "spring")

public abstract class LegConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegConverter.class);

    private static final String DEFAULT_VALUE = null;
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String EXCHANGE_IDENTIFIER = "EXCHANGE";

    @Resource(name = "abpLegQuotingDataConverter")
    private LegQuotingDataConverter legQuotingDataConverter;
    @Resource(name = "abpOddsConverter")
    private OddsConverter oddsConverter;
    @Resource(name = "abpFractionalOddsConverter")
    private FractionalOddsConverter fractionalOddsConverter;
    @Resource(name = "javaValueToProtoValueConverter")
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Resource(name = "abpLegTypeConverter")
    private LegTypeConverter legTypeConverter;

    @Mapping(target = "legQuotingData", source = "part", qualifiedByName = "convertLegQuotingData")
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "legType", source = "part", qualifiedByName = "convertLegType")
    @Mapping(target = "exchangeMarketId", source = "part.marketInfo.entity.idsMap", qualifiedByName = "convertExchangeMarketId",
    conditionExpression = "java(hasExchangeMarketId(part))")
    @Mapping(target = "startingPrice", source = "part.fractionalPrice.starting", qualifiedByName = "convertFractionToOdds")
    @Mapping(target = "eachwayPlaces", source = "part.eachwayInfo.eachwayPlaces")
    @Mapping(target = "frontEndEachwayFactor", source = "part.eachwayInfo.eachwayFactor", qualifiedByName = "convertToFractionalOdds")
    @Mapping(target = "eventDescription", source = "part.eventInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "eventMarketDescription", source = "part.marketInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "selectionName", source = "part.selectionInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "startTime", source = "part.eventInfo.startTime", qualifiedByName = "convertStartTime")
    @Mapping(target = "handicap", source = "part.handicap")
    @Mapping(target = "sportName", source = "part.sportInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "competitionName", source = "part.competitionInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract Leg convert(PartOuterClass.Part part, @Context LegOuterClass.Leg leg);


    @Named("convertLegQuotingData")
    protected LegQuotingData convertLegQuotingData(PartOuterClass.Part part, @Context LegOuterClass.Leg leg) {
        return legQuotingDataConverter.convert(leg, part);
    }

    @Named("convertLegType")
    protected LegType convertLegType(PartOuterClass.Part ignoredUnused, @Context LegOuterClass.Leg leg) {
        return legTypeConverter.convert(leg.getLegType());
    }

    protected boolean hasExchangeMarketId(PartOuterClass.Part part) {
        return Optional.of(part)
                .filter(PartOuterClass.Part::hasMarketInfo)
                .map(PartOuterClass.Part::getMarketInfo)
                .filter(PartOuterClass.MarketInfo::hasEntity)
                .map(PartOuterClass.MarketInfo::getEntity)
                .map(EntityOuterClass.Entity::getIdsMap)
                .filter(map -> map.get(EXCHANGE_IDENTIFIER) != null)
                .isPresent();
    }

    @Named("convertExchangeMarketId")
    protected StringValue convertExchangeMarketId(Map<String, String> exchangeMarketIdsMap) {
        return StringValue.of(exchangeMarketIdsMap.getOrDefault(EXCHANGE_IDENTIFIER, DEFAULT_VALUE));
    }

    @Named("convertToStringValue")
    protected StringValue convertToStringValue(String str) {
        return javaValueToProtoValueConverter.convertToStringValue(str);
    }

    @Named("convertFractionToOdds")
    protected Odds convertFractionToOdds(FractionOuterClass.Fraction fraction) {
        return oddsConverter.convert(fraction);
    }

    @Named("convertToFractionalOdds")
    protected FractionalOdds convertToFractionalOdds(FractionOuterClass.Fraction fraction) {
        return fractionalOddsConverter.convert(fraction);
    }

    @Named("convertStartTime")
    protected Timestamp convertStartTime(long startTime) {
        return javaValueToProtoValueConverter.convertToTimestamp(startTime);
    }

    @AfterMapping
    protected void setEventId(PartOuterClass.Part part, @MappingTarget Leg.Builder target) {
        try {
            Int64Value eventId = Int64Value.of(Long.parseLong(part.getEventInfo().getEntity().getIdsMap().get(SPORTEX_IDENTIFIER)));
            target.setEventId(eventId);
        } catch (NumberFormatException exception) {
            LOGGER.debug("operation=setEventId, msg='Unable to convert eventId {} to long value.'",
                    part.getEventInfo().getEntity().getIdsMap().get(SPORTEX_IDENTIFIER),  exception);
        }
    }

}
