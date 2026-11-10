package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.OddsConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details.OutcomeBasedDetailsConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegType;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.flutter.gbp.fcq.bet.outcome.details.OutcomeBasedDetailsOuterClass.OutcomeBasedDetails;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
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
import java.util.Optional;

/**
 * Converts an FBP leg of type {@code OUTCOME_BASED} into the single CBD Leg it represents.
 * <p>
 * An outcome-based leg is a self-contained multiple choice group: its Parts are the EMS selections and OBP-native
 * choices that make up one quoted unit, so the leg becomes exactly one CBD Leg carrying every Part (see
 * {@link OutcomeBasedLegQuotingDataConverter}). The identifiers are taken from the first Part and the sport name from
 * the first Part that carries one; the descriptive and eachway attributes of the individual Parts are Part level state
 * and are not copied onto the leg.
 * <p>
 * Legs of any other type are converted by {@link MarketBasedLegConverter}.
 */
@Mapper(implementationName = "OutcomeBasedLegConverterImpl", componentModel = "spring")
public abstract class OutcomeBasedLegConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutcomeBasedLegConverter.class);

    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String RAMP_IDENTIFIER = "RAMP";

    @Resource(name = "fbpOutcomeBasedLegQuotingDataConverter")
    private OutcomeBasedLegQuotingDataConverter legQuotingDataConverter;
    @Resource(name = "fbpOddsConverter")
    private OddsConverter oddsConverter;
    @Resource(name = "javaValueToProtoValueConverter")
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Resource(name = "fbpLegTypeConverter")
    private LegTypeConverter legTypeConverter;
    @Resource(name = "fbpOutcomeBasedDetailsConverter")
    private OutcomeBasedDetailsConverter outcomeBasedDetailsConverter;

    /**
     * Converts an outcome-based FBP leg into the single CBD Leg it represents.
     *
     * @param leg the FBP leg to convert
     * @param betId the id of the bet being converted, used to correlate anything logged during the conversion of this
     *              leg back to a bet; the FBP leg carries no bet identifier and leg numbers are bet scoped
     * @return the CBD leg converted from this FBP leg; empty when the leg carries no Parts
     */
    public List<Leg> convert(LegOuterClass.Leg leg, String betId) {
        if (leg.getPartCount() == 0) {
            return List.of();
        }
        return List.of(convertToSingleLeg(leg, betId));
    }

    @Mapping(target = "legQuotingData", source = "leg", qualifiedByName = "convertLegQuotingData")
    @Mapping(target = "legType", source = "leg", qualifiedByName = "convertLegType")
    @Mapping(target = "outcomeBasedDetails", source = "leg", qualifiedByName = "convertOutcomeBasedDetails",
            conditionExpression = "java(leg.hasOutcomeBasedDetails())")
    // the FBP and CBD LegType enums do not share their numbering, so the raw FBP legType number must not be copied
    // over the converted legType
    @Mapping(target = "legTypeValue", ignore = true)
    @Mapping(target = "handicap", ignore = true)
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "exchangeMarketId", ignore = true)
    // startingPrice is resolved from the leg (or, for a single part leg, that part) fractional price;
    // see setStartingPrice
    @Mapping(target = "startingPrice", ignore = true)
    // sportName is resolved from the first part that carries one; see setSportName
    @Mapping(target = "sportName", ignore = true)
    @Mapping(target = "eachwayPlaces", ignore = true)
    @Mapping(target = "frontEndEachwayFactor", ignore = true)
    @Mapping(target = "eventDescription", ignore = true)
    @Mapping(target = "eventMarketDescription", ignore = true)
    @Mapping(target = "selectionName", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "competitionName", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    protected abstract Leg convertToSingleLeg(LegOuterClass.Leg leg, @Context String betId);

    @Named("convertLegQuotingData")
    protected LegQuotingData convertLegQuotingData(LegOuterClass.Leg leg, @Context String betId) {
        return legQuotingDataConverter.convert(leg, betId);
    }

    @Named("convertLegType")
    protected LegType convertLegType(LegOuterClass.Leg leg) {
        return legTypeConverter.convert(leg.getLegType());
    }

    @Named("convertOutcomeBasedDetails")
    protected OutcomeBasedDetails convertOutcomeBasedDetails(LegOuterClass.Leg leg) {
        return outcomeBasedDetailsConverter.convert(leg.getOutcomeBasedDetails(), getEventRampId(leg).orElse(null));
    }

    @Named("convertToStringValue")
    protected StringValue convertToStringValue(String str) {
        return javaValueToProtoValueConverter.convertToStringValue(str);
    }

    @Named("convertFractionToOdds")
    protected Odds convertFractionToOdds(FractionOuterClass.Fraction fraction) {
        return oddsConverter.convert(fraction);
    }

    @AfterMapping
    protected void setStartingPrice(LegOuterClass.Leg leg, @Context String betId, @MappingTarget Leg.Builder target) {
        FractionalPriceOuterClass.FractionalPrice fractionalPrice = FbpFractionalPriceResolver.resolveForGroup(leg, betId);
        if (fractionalPrice != null && fractionalPrice.hasStarting()) {
            target.setStartingPrice(convertFractionToOdds(fractionalPrice.getStarting()));
        }
    }

    /**
     * Sets the sport name of the leg, taken from the first Part that carries one.
     * <p>
     * The Parts of a multiple choice group are not all attached to the same entity hierarchy, so the sport name is
     * not guaranteed to be present on the first Part. It is left unset when no Part of the leg carries one.
     *
     * @param leg the FBP leg being converted
     * @param target the target CBD leg builder
     */
    @AfterMapping
    protected void setSportName(LegOuterClass.Leg leg, @MappingTarget Leg.Builder target) {
        getSportName(leg).ifPresent(sportName -> target.setSportName(convertToStringValue(sportName)));
    }

    @AfterMapping
    protected void setEventId(LegOuterClass.Leg leg, @Context String betId, @MappingTarget Leg.Builder target) {
        String eventId = getEventSportexId(leg).orElse(null);
        try {
            target.setEventId(Int64Value.of(Long.parseLong(eventId)));
        } catch (NumberFormatException exception) {
            LOGGER.debug("operation=setEventId, msg='Unable to convert eventId to long value.', betId={}, legNumber={}, eventId={}",
                    betId, leg.getLegNumber(), eventId, exception);
        }
    }

    @AfterMapping
    protected void setEventRampId(LegOuterClass.Leg leg, @MappingTarget Leg.Builder target) {
        getEventRampId(leg).ifPresent(eventRampId -> target.setEventRampId(StringValue.of(eventRampId)));
    }

    private Optional<String> getSportName(LegOuterClass.Leg leg) {
        return leg.getPartList().stream()
                .filter(PartOuterClass.Part::hasSportInfo)
                .map(PartOuterClass.Part::getSportInfo)
                .filter(PartOuterClass.SportInfo::hasEntity)
                .map(PartOuterClass.SportInfo::getEntity)
                .map(PartOuterClass.Entity::getName)
                .filter(sportName -> !sportName.isEmpty())
                .findFirst();
    }

    private Optional<String> getEventSportexId(LegOuterClass.Leg leg) {
        return getEventEntityId(leg, SPORTEX_IDENTIFIER);
    }

    private Optional<String> getEventRampId(LegOuterClass.Leg leg) {
        return getEventEntityId(leg, RAMP_IDENTIFIER);
    }

    private Optional<String> getEventEntityId(LegOuterClass.Leg leg, String identifier) {
        return firstPart(leg)
                .map(PartOuterClass.Part::getEventInfo)
                .map(PartOuterClass.EventInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .map(idsMap -> idsMap.get(identifier));
    }

    private Optional<PartOuterClass.Part> firstPart(LegOuterClass.Leg leg) {
        return leg.getPartList().stream().findFirst();
    }
}
