package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.fbp.FractionalOddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.OddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.OutcomeBasedDetailsConverter;
import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegType;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.flutter.gbp.fcq.bet.outcome.details.OutcomeBasedDetailsOuterClass.OutcomeBasedDetails;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import java.util.function.BooleanSupplier;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts an FBP leg of any type other than {@code OUTCOME_BASED} into one CBD Leg per FBP Part.
 * <p>
 * Each Part of such a leg is an independent selection with its own market, price and result, so each one becomes
 * its own CBD Leg. Most of these legs carry a single Part.
 * <p>
 * Outcome-based legs are converted by {@link OutcomeBasedLegConverter}.
 */
@Mapper(implementationName = "MarketBasedLegConverterImpl", componentModel = "spring")
public abstract class MarketBasedLegConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketBasedLegConverter.class);

    private static final String DEFAULT_VALUE = null;
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String EXCHANGE_IDENTIFIER = "EXCHANGE";
    private static final String RAMP_IDENTIFIER = "RAMP";
    private static final String MISC_IDENTIFIER = "MISC";
    private static final String BMS_IDENTIFIER = "OPENBET";

    @Resource(name = "fbpMarketBasedLegQuotingDataConverter")
    private MarketBasedLegQuotingDataConverter legQuotingDataConverter;
    @Resource(name = "fbpOddsConverter")
    private OddsConverter oddsConverter;
    @Resource(name = "fbpFractionalOddsConverter")
    private FractionalOddsConverter fractionalOddsConverter;
    @Resource(name = "javaValueToProtoValueConverter")
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Resource(name = "fbpLegTypeConverter")
    private LegTypeConverter legTypeConverter;
    @Resource(name = "fbpOutcomeBasedDetailsConverter")
    private OutcomeBasedDetailsConverter outcomeBasedDetailsConverter;
    @Resource(name = "fcq.featureToggle.enable.market.based.obp.bets")
    private BooleanSupplier enableMarketBasedObpBetsToggle;

    /**
     * Converts a market-based FBP leg into the CBD legs it represents, one per FBP Part.
     *
     * @param leg the FBP leg to convert
     * @param superSubEligibleRampIds the bet's SuperSub eligible selection ramp ids
     * @param betId the id of the bet being converted, used to correlate anything logged during the conversion of this
     *              leg back to a bet; the FBP leg carries no bet identifier and leg numbers are bet scoped
     * @return the CBD legs converted from this FBP leg; empty when the leg carries no Parts
     */
    public List<Leg> convert(LegOuterClass.Leg leg, Set<String> superSubEligibleRampIds, String betId) {
        return leg.getPartList().stream()
                .map(part -> convertPartToLeg(part, leg, superSubEligibleRampIds, betId))
                .collect(Collectors.toList());
    }

    @Mapping(target = "handicap", source = "part.handicap")
    @Mapping(target = "legQuotingData", source = "part", qualifiedByName = "convertLegQuotingData")
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "legType", source = "part", qualifiedByName = "convertLegType")
    // An OBP-enriched EMS leg carries the OBP choices and outcome expression that price it; see convertOutcomeBasedDetails
    @Mapping(target = "outcomeBasedDetails", source = "part", qualifiedByName = "convertOutcomeBasedDetails",
            conditionExpression = "java(leg.hasOutcomeBasedDetails())")
    @Mapping(target = "exchangeMarketId", source = "part.marketInfo.entity.idsMap", qualifiedByName = "convertExchangeMarketId",
            conditionExpression = "java(hasExchangeMarketId(part))")
    // startingPrice is resolved from the leg (or, if absent, the part) fractional price; see setStartingPrice
    @Mapping(target = "startingPrice", ignore = true)
    @Mapping(target = "eachwayPlaces", source = "part.eachwayInfo.eachwayPlaces")
    @Mapping(target = "frontEndEachwayFactor", source = "part.eachwayInfo.eachwayFactor", qualifiedByName = "convertToFractionalOdds")
    @Mapping(target = "eventDescription", source = "part.eventInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "eventMarketDescription", source = "part.marketInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "selectionName", source = "part.selectionInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "startTime", source = "part.eventInfo.startTime", qualifiedByName = "convertStartTime")
    @Mapping(target = "sportName", source = "part.sportInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "competitionName", source = "part.competitionInfo.entity.name", qualifiedByName = "convertToStringValue")
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    protected abstract Leg convertPartToLeg(PartOuterClass.Part part, @Context LegOuterClass.Leg leg,
                                            @Context Set<String> superSubEligibleRampIds, @Context String betId);

    @Named("convertLegQuotingData")
    protected LegQuotingData convertLegQuotingData(PartOuterClass.Part part, @Context LegOuterClass.Leg leg, @Context String betId) {
        return legQuotingDataConverter.convert(leg, part, betId);
    }

    @Named("convertLegType")
    protected LegType convertLegType(PartOuterClass.Part ignoredUnused, @Context LegOuterClass.Leg leg) {
        return legTypeConverter.convert(leg.getLegType());
    }

    /**
     * Converts the leg's outcome-based details, resolving the event hierarchy against the Part's event ramp id.
     * <p>
     * Outcome-based details are not exclusive to {@code OUTCOME_BASED} legs: a market-based (EMS) leg that has been
     * OBP-enriched carries the OBP choices and outcome expression that price it.
     *
     * @param part the FBP Part the CBD Leg is being converted from
     * @param leg the FBP leg being converted
     * @return the converted outcome-based details
     */
    @Named("convertOutcomeBasedDetails")
    protected OutcomeBasedDetails convertOutcomeBasedDetails(PartOuterClass.Part part, @Context LegOuterClass.Leg leg) {
        return outcomeBasedDetailsConverter.convert(leg.getOutcomeBasedDetails(), getEventRampId(part).orElse(null));
    }

    protected boolean hasExchangeMarketId(PartOuterClass.Part part) {
        return Optional.of(part)
                .filter(PartOuterClass.Part::hasMarketInfo)
                .map(PartOuterClass.Part::getMarketInfo)
                .filter(PartOuterClass.MarketInfo::hasEntity)
                .map(PartOuterClass.MarketInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
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
    protected void setStartingPrice(PartOuterClass.Part part, @Context LegOuterClass.Leg leg, @MappingTarget Leg.Builder target) {
        FractionalPriceOuterClass.FractionalPrice fractionalPrice = FbpFractionalPriceResolver.resolveForPart(leg, part);
        if (fractionalPrice != null && fractionalPrice.hasStarting()) {
            target.setStartingPrice(convertFractionToOdds(fractionalPrice.getStarting()));
        }
    }

    @AfterMapping
    protected void setEventId(PartOuterClass.Part part, @Context LegOuterClass.Leg leg, @Context String betId,
                             @MappingTarget Leg.Builder target) {
        try {
            Int64Value eventId = Int64Value.of(Long.parseLong(part.getEventInfo().getEntity().getIdsMap().get(SPORTEX_IDENTIFIER)));
            target.setEventId(eventId);
        } catch (NumberFormatException exception) {
            LOGGER.debug("operation=setEventId, msg='Unable to convert eventId to long value.', betId={}, legNumber={}, eventId={}",
                    betId, leg == null ? null : leg.getLegNumber(),
                    part.getEventInfo().getEntity().getIdsMap().get(SPORTEX_IDENTIFIER), exception);
        }
    }

    @AfterMapping
    protected void setSuperSubEligibility(PartOuterClass.Part part, @MappingTarget Leg.Builder target,
                                          @Context Set<String> superSubEligibleRampIds) {
        getLegRampId(part).ifPresent(selectionRampId -> target.setIsSuperSubEligible(superSubEligibleRampIds.contains(selectionRampId)));
    }

    @AfterMapping
    protected void setEventRampId(PartOuterClass.Part part, @MappingTarget Leg.Builder target) {
        getEventRampId(part).ifPresent(eventRampId -> target.setEventRampId(StringValue.of(eventRampId)));
    }

    @AfterMapping
    protected void setSelectionBmsId(PartOuterClass.Part part, @MappingTarget Leg.Builder target) {
        getSelectionBmsId(part).ifPresent(selectionBmsId -> target.setSelectionBmsId(StringValue.of(selectionBmsId)));
    }

    @AfterMapping
    protected void setIsMarketBasedOBPLeg(PartOuterClass.Part part, @MappingTarget Leg.Builder target) {
        if (enableMarketBasedObpBetsToggle.getAsBoolean()) {
            getMiscFeedId(part).ifPresent(miscFeedId -> target.setIsMarketBasedObpLeg(true));
        }
    }

    private Optional<String> getEventRampId(PartOuterClass.Part part) {
        return Optional.ofNullable(part)
                .map(PartOuterClass.Part::getEventInfo)
                .map(PartOuterClass.EventInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .map(stringStringMap -> stringStringMap.get(RAMP_IDENTIFIER));
    }

    private Optional<String> getMiscFeedId(PartOuterClass.Part part) {
        return Optional.ofNullable(part)
                .map(PartOuterClass.Part::getSelectionInfo)
                .map(PartOuterClass.SelectionInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .map(stringStringMap -> stringStringMap.get(MISC_IDENTIFIER));
    }

    private Optional<String> getSelectionBmsId(PartOuterClass.Part part) {
        return Optional.ofNullable(part)
                .map(PartOuterClass.Part::getSelectionInfo)
                .map(PartOuterClass.SelectionInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .map(idsMap -> idsMap.get(BMS_IDENTIFIER));
    }

    private Optional<String> getLegRampId(PartOuterClass.Part part) {
        return Optional.ofNullable(part)
                .map(PartOuterClass.Part::getSelectionInfo)
                .map(PartOuterClass.SelectionInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .map(stringStringMap -> stringStringMap.get(RAMP_IDENTIFIER));
    }
}
