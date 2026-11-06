package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.LegStatusConverter;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.flutter.gbp.fcq.bet.leg.Part;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import jakarta.annotation.Resource;

/**
 * Converts the quoting data of a market-based FBP leg, i.e. a leg of any type other than {@code OUTCOME_BASED}.
 * <p>
 * Each Part of such a leg is an independent selection with its own market, price and result, and
 * {@link MarketBasedLegConverter} expands the leg into one CBD Leg per FBP Part. The quoting data therefore
 * describes a single Part: it carries that Part alone, and the Part is where the result and the eachway, rule 4
 * and combination terms come from.
 * <p>
 * The quoting data of an outcome-based leg is converted by {@link OutcomeBasedLegQuotingDataConverter}.
 */
@Mapper(implementationName = "MarketBasedLegQuotingDataConverterImpl", componentModel = "spring")
public abstract class MarketBasedLegQuotingDataConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketBasedLegQuotingDataConverter.class);

    @Resource(name = "fbpLegStatusConverter")
    private LegStatusConverter<PartOuterClass.Part.Result> legStatusConverter;
    @Resource(name = "fbpFractionalPriceWriter")
    private FbpFractionalPriceWriter fractionalPriceWriter;
    @Resource(name = "fbpPartConverter")
    private PartConverter partConverter;

    /**
     * Converts the quoting data of the CBD Leg that an FBP Part of a market-based leg becomes.
     *
     * @param leg the FBP leg being converted
     * @param part the FBP Part the CBD Leg is being converted from
     * @param betId the id of the bet being converted, used to correlate anything logged here back to a bet; the FBP
     *              leg carries no bet identifier and leg numbers are bet scoped
     * @return the converted quoting data
     */
    @Mapping(target = "legNumber", source = "leg.legNumber")
    @Mapping(target = "legStatus", source = "part.result", qualifiedByName = "convertLegStatus")
    // marketType is Part state now; the legacy LegQuotingData field is kept in the proto but no longer written
    @Mapping(target = "marketType", ignore = true)
    // price fields are resolved from the leg (or, if absent, the part) fractional price; see applyFractionalPrice
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "originalPrice", ignore = true)
    @Mapping(target = "startPrice", ignore = true)
    @Mapping(target = "originalBasePrice", ignore = true)
    @Mapping(target = "priceType", ignore = true)
    @Mapping(target = "rule4Deductions", source = "part.rule4Deductions")
    @Mapping(target = "deadHeatWinDeductions", source = "part.deadHeatWinDeductions")
    @Mapping(target = "deadHeatEachwayDeductions", source = "part.eachwayInfo.deadHeatEachwayDeductions", qualifiedByName =
            "convertDeadHeatEachwayDeductions")
    @Mapping(target = "combinationNumber", source = "part.noCombi.value", qualifiedByName = "convertCombinationNumber")
    @Mapping(target = "eachwayDecimalFactor", source = "part.eachwayInfo.eachwayFactor", qualifiedByName = "convertEachWayDecimalFactor")
    @Mapping(target = "eachwayOffset", source = "part.eachwayInfo.eachwayPlaceOffset.value")
    // selectionId + marketId deprecated proto fields
    @Mapping(target = "selectionId", ignore = true)
    @Mapping(target = "marketId", ignore = true)
    // the Part list carries only the Part this leg was converted from; see addPart
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract LegQuotingData convert(LegOuterClass.Leg leg, PartOuterClass.Part part, @Context String betId);

    @Named("convertLegStatus")
    protected LegStatus convertLegStatus(PartOuterClass.Part.Result result) {
        return legStatusConverter.convert(result);
    }

    @Named("convertDeadHeatEachwayDeductions")
    protected double convertDeadHeatEachwayDeductions(DoubleValue dbl) {
        return dbl.getValue();
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

    /**
     * Populates the price-related LegQuotingData fields from the FractionalPrice pricing this Part, which is the
     * leg's combined price or, when the leg carries none, the Part's own price.
     *
     * @param leg the FBP leg being converted; may be {@code null}
     * @param part the FBP Part this LegQuotingData was converted from; may be {@code null}
     * @param builder the target LegQuotingData builder
     */
    @AfterMapping
    protected void applyFractionalPrice(LegOuterClass.Leg leg, PartOuterClass.Part part,
                                        @MappingTarget LegQuotingData.Builder builder) {
        fractionalPriceWriter.write(FbpFractionalPriceResolver.resolveForPart(leg, part), builder);
    }

    /**
     * Appends the CBD proto Part this leg is quoted from.
     * <p>
     * A market-based leg is expanded into one CBD Leg per FBP Part, so each converted leg carries only its own Part,
     * contributing that selection's market and selection ids. A Part that carries none of those identifiers is
     * dropped by {@link PartConverter}.
     *
     * @param leg the FBP leg being converted
     * @param fbpPart the FBP Part this LegQuotingData was converted from; may be {@code null}
     * @param betId the id of the bet being converted, logged to identify the bet a dropped Part belongs to
     * @param builder the target LegQuotingData builder
     */
    @AfterMapping
    protected void addPart(LegOuterClass.Leg leg, PartOuterClass.Part fbpPart, @Context String betId,
                           @MappingTarget LegQuotingData.Builder builder) {
        Part convertedPart = partConverter.convert(fbpPart);
        if (convertedPart == null) {
            // A Part is dropped when it carries no marketId, selectionId, choiceIdRef or marketType, so the Part
            // itself has nothing to identify it by: only the bet and its position within it can be reported.
            LOGGER.warn("operation=addPart, msg='Part not converted for leg', betId={}, legNumber={}, partNumber={}",
                    betId, leg == null ? null : leg.getLegNumber(), fbpPart == null ? null : fbpPart.getPartNumber());
            return;
        }
        builder.addParts(convertedPart);
    }
}
