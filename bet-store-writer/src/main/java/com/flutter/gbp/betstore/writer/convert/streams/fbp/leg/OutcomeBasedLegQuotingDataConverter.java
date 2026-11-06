package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.LegStatusConverter;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.flutter.gbp.fcq.bet.leg.Part;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Converts the quoting data of an FBP leg of type {@code OUTCOME_BASED}.
 * <p>
 * An outcome-based leg is a self-contained multiple choice group quoted as one unit, and
 * {@link OutcomeBasedLegConverter} converts it into exactly one CBD Leg. The quoting data therefore carries every
 * Part of the leg — EMS Parts contribute their market and selection ids, OBP-native Parts contribute their choice
 * id — while the price terms and the result come from the leg itself: the whole group is resulted as one unit, so
 * the Part results are not the result of the leg. The eachway, rule 4 and combination terms are Part level state
 * and are no longer written at LegQuotingData level.
 * <p>
 * The quoting data of a leg of any other type is converted by {@link MarketBasedLegQuotingDataConverter}.
 */
@Mapper(implementationName = "OutcomeBasedLegQuotingDataConverterImpl", componentModel = "spring")
public abstract class OutcomeBasedLegQuotingDataConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutcomeBasedLegQuotingDataConverter.class);

    @Resource(name = "fbpLegStatusConverter")
    private LegStatusConverter<LegOuterClass.Leg.Result> legStatusConverter;
    @Resource(name = "fbpFractionalPriceWriter")
    private FbpFractionalPriceWriter fractionalPriceWriter;
    @Resource(name = "fbpPartConverter")
    private PartConverter partConverter;

    /**
     * Converts the quoting data of the single CBD Leg an outcome-based leg becomes.
     *
     * @param leg the FBP leg being converted
     * @param betId the id of the bet being converted, used to correlate anything logged here back to a bet; the FBP
     *              leg carries no bet identifier and leg numbers are bet scoped
     * @return the converted quoting data
     */
    @Mapping(target = "legNumber", source = "leg.legNumber")
    @Mapping(target = "legStatus", source = "leg.result", qualifiedByName = "convertLegStatus")
    // marketType is Part state now; the legacy LegQuotingData field is kept in the proto but no longer written
    @Mapping(target = "marketType", ignore = true)
    // price fields are resolved from the leg (or, for a single part leg, that part) fractional price;
    // see applyFractionalPrice
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "originalPrice", ignore = true)
    @Mapping(target = "startPrice", ignore = true)
    @Mapping(target = "originalBasePrice", ignore = true)
    @Mapping(target = "priceType", ignore = true)
    @Mapping(target = "rule4Deductions", ignore = true)
    @Mapping(target = "deadHeatWinDeductions", ignore = true)
    @Mapping(target = "deadHeatEachwayDeductions", ignore = true)
    @Mapping(target = "combinationNumber", ignore = true)
    @Mapping(target = "eachwayDecimalFactor", ignore = true)
    @Mapping(target = "eachwayOffset", ignore = true)
    // selectionId + marketId deprecated proto fields
    @Mapping(target = "selectionId", ignore = true)
    @Mapping(target = "marketId", ignore = true)
    // the Part list carries every Part of the multiple choice group; see addParts
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    public abstract LegQuotingData convert(LegOuterClass.Leg leg, @Context String betId);

    @Named("convertLegStatus")
    protected LegStatus convertLegStatus(LegOuterClass.Leg.Result result) {
        return legStatusConverter.convert(result);
    }

    /**
     * Populates the price-related LegQuotingData fields from the FractionalPrice pricing the whole multiple choice
     * group, which is the leg's combined price or, when the leg carries none and has a single Part, that Part's price.
     *
     * @param leg the FBP leg being converted; may be {@code null}
     * @param betId the id of the bet being converted, logged when no price can be resolved for the group
     * @param builder the target LegQuotingData builder
     */
    @AfterMapping
    protected void applyFractionalPrice(LegOuterClass.Leg leg, @Context String betId, @MappingTarget LegQuotingData.Builder builder) {
        fractionalPriceWriter.write(FbpFractionalPriceResolver.resolveForGroup(leg, betId), builder);
    }

    /**
     * Appends every CBD proto Part of the multiple choice group, preserving the FBP Part order.
     * <p>
     * The whole group is quoted as one unit, so all of the leg's Parts land on the single CBD Leg it converts to:
     * EMS Parts contribute their market and selection ids, OBP-native Parts contribute their choice id. Parts that
     * carry none of those identifiers are dropped by {@link PartConverter}.
     *
     * @param leg the FBP leg being converted
     * @param betId the id of the bet being converted, logged to identify the bet dropped Parts belong to
     * @param builder the target LegQuotingData builder
     */
    @AfterMapping
    protected void addParts(LegOuterClass.Leg leg, @Context String betId, @MappingTarget LegQuotingData.Builder builder) {
        if (leg == null) {
            return;
        }
        List<Part> convertedParts = leg.getPartList().stream()
                .map(partConverter::convert)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (convertedParts.isEmpty()) {
            // Parts are dropped when they carry no marketId, selectionId, choiceIdRef or marketType, so the Parts
            // themselves have nothing to identify them by: only the bet and the leg's position within it can be reported.
            LOGGER.warn("operation=addParts, msg='No Parts converted for leg', betId={}, legNumber={}, partCount={}",
                    betId, leg.getLegNumber(), leg.getPartCount());
            return;
        }
        convertedParts.forEach(builder::addParts);
    }
}
