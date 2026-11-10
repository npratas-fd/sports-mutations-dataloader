package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.LegStatusConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.PriceTypeConverter;
import com.flutter.gbp.fcq.bet.Price;
import com.flutter.gbp.fcq.bet.PriceType;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.retail.proto.bet.EntityOuterClass;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import javax.annotation.Nonnull;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Optional;

@Mapper(implementationName = "LegQuotingDataConverterImpl", componentModel = "spring")
public abstract class LegQuotingDataConverter {

    private static final String DEFAULT_VALUE = null;
    private static final String DEFAULT_EACHWAY_OFFSET = "0";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";

    @Resource(name = "abpLegStatusConverter")
    private LegStatusConverter<PartOuterClass.Part.Result> legStatusConverter;
    @Resource(name = "abpPriceTypeConverter")
    private PriceTypeConverter priceTypeConverter;
    @Resource(name = "abpStartPriceConverter")
    private StartPriceConverter startPriceConverter;

    @Mapping(target = "selectionId", source = "part.selectionInfo.entity.idsMap", qualifiedByName = "convertSelectionId",
    conditionExpression = "java(hasSelectionId(part))")
    @Mapping(target = "legNumber", source = "leg.legNumber")
    @Mapping(target = "legStatus", source = "part.result", qualifiedByName = "convertLegStatus")
    @Mapping(target = "marketId", source = "part.marketInfo.entity.idsMap", qualifiedByName = "convertMarketId",
            conditionExpression = "java(hasMarketId(part))")
    @Mapping(target = "marketType", source = "part.marketTypeInfo.entity.idsMap", qualifiedByName = "convertMarketType",
            conditionExpression = "java(hasMarketType(part))")
    @Mapping(target = "price", source = "part.fractionalPrice.taken", qualifiedByName = "convertFractionToPrice")
    @Mapping(target = "originalPrice", source = "part.fractionalPrice.taken", qualifiedByName = "convertFractionToPrice")
    @Mapping(target = "startPrice", source = "part.fractionalPrice.starting", qualifiedByName = "convertStartPrice")
    @Mapping(target = "priceType", source = "part.fractionalPrice.type", qualifiedByName = "convertPriceType")
    @Mapping(target = "rule4Deductions", source = "part.rule4Deductions")
    @Mapping(target = "deadHeatWinDeductions", source = "part.deadHeatWinDeductions")
    @Mapping(target = "deadHeatEachwayDeductions", source = "part.eachwayInfo.deadHeatEachwayDeductions", qualifiedByName =
            "convertDeadHeatEachwayDeductions")
    @Mapping(target = "combinationNumber", source = "part.noCombi.value", qualifiedByName = "convertCombinationNumber")
    @Mapping(target = "eachwayDecimalFactor", source = "part.eachwayInfo.eachwayFactor", qualifiedByName = "convertEachWayDecimalFactor")
    @Mapping(target = "eachwayOffset", constant = DEFAULT_EACHWAY_OFFSET)
    @Mapping(target = "originalBasePrice", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract LegQuotingData convert(LegOuterClass.Leg leg, PartOuterClass.Part part);


    @Named("convertLegStatus")
    protected LegStatus convertLegStatus(PartOuterClass.Part.Result result) {
        return legStatusConverter.convert(result);
    }

    protected boolean hasMarketId(PartOuterClass.Part part) {
        return Optional.of(part)
                .filter(PartOuterClass.Part::hasMarketInfo)
                .map(PartOuterClass.Part::getMarketInfo)
                .filter(PartOuterClass.MarketInfo::hasEntity)
                .map(PartOuterClass.MarketInfo::getEntity)
                .map(EntityOuterClass.Entity::getIdsMap)
                .filter(map -> map.get(SPORTSBOOK_IDENTIFIER) != null)
                .isPresent();
    }

    @Named("convertMarketId")
    protected StringValue convertMarketId(Map<String, String> marketIdsMap) {
        return StringValue.of(marketIdsMap.getOrDefault(SPORTSBOOK_IDENTIFIER, DEFAULT_VALUE));
    }

    protected boolean hasSelectionId(PartOuterClass.Part part) {
        return Optional.of(part)
                .filter(PartOuterClass.Part::hasSelectionInfo)
                .map(PartOuterClass.Part::getSelectionInfo)
                .filter(PartOuterClass.SelectionInfo::hasEntity)
                .map(PartOuterClass.SelectionInfo::getEntity)
                .map(EntityOuterClass.Entity::getIdsMap)
                .filter(map -> map.get(SPORTEX_IDENTIFIER) != null)
                .isPresent();
    }

    @Named("convertSelectionId")
    protected long convertSelectionId(Map<String, String> selectionIdsMap) {
        try {
            return Long.parseLong(selectionIdsMap.getOrDefault(SPORTEX_IDENTIFIER, DEFAULT_VALUE));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    protected boolean hasMarketType(PartOuterClass.Part part) {
        return Optional.of(part)
                .filter(PartOuterClass.Part::hasMarketTypeInfo)
                .map(PartOuterClass.Part::getMarketTypeInfo)
                .filter(PartOuterClass.MarketTypeInfo::hasEntity)
                .map(PartOuterClass.MarketTypeInfo::getEntity)
                .map(EntityOuterClass.Entity::getIdsMap)
                .filter(map -> map.get(SPORTSBOOK_IDENTIFIER) != null)
                .isPresent();
    }

    @Named("convertMarketType")
    protected StringValue convertMarketType(Map<String, String> marketTypeIdsMap) {
        return StringValue.of(marketTypeIdsMap.getOrDefault(SPORTSBOOK_IDENTIFIER, DEFAULT_VALUE));
    }


    @Named("convertDeadHeatEachwayDeductions")
    protected double convertDeadHeatEachwayDeductions(DoubleValue dbl) {
        return dbl.getValue();
    }

    @Named("convertFractionToPrice")
    protected Price convertFractionToPrice(@Nonnull FractionOuterClass.Fraction fraction) {
        return Price.newBuilder()
                .setNumerator(fraction.getNumerator())
                .setDenominator(fraction.getDenominator())
                .build();
    }

    @Named("convertStartPrice")
    protected double convertStartPrice(@Nonnull FractionOuterClass.Fraction startPriceFractional) {
        return startPriceConverter.convertStartPrice(startPriceFractional);
    }

    @Named("convertPriceType")
    protected PriceType convertPriceType(FractionalPriceOuterClass.FractionalPrice.PriceType priceType) {
        return priceTypeConverter.convert(priceType);
    }

    @Named("convertCombinationNumber")
    protected StringValue convertCombinationNumber(int combinationNumber) {
        return StringValue.of(String.valueOf(combinationNumber));
    }

    @Named("convertEachWayDecimalFactor")
    protected double convertEachWayDecimalFactor(@Nonnull FractionOuterClass.Fraction fraction) {
        if (fraction.getNumerator() == 0 || fraction.getDenominator() == 0) {
            return 1d;
        }

        return (double) fraction.getNumerator() / fraction.getDenominator();
    }
}
