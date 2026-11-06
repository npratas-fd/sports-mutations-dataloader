package com.flutter.gbp.betstore.writer.convert.streams.fbp.bet;

import com.flutter.gbp.betstore.writer.convert.streams.fbp.RiskContextConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.AccaFreezeEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.EligibleJurisdictionConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.FundingTypeConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.OddsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.BetContextEnricherChain;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.MarketBasedLegConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.OutcomeBasedLegConverter;
import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.BetQuotingData;
import com.flutter.gbp.fcq.bet.BetStatus;
import com.flutter.gbp.fcq.bet.FundingType;
import com.flutter.gbp.fcq.bet.Jurisdiction;
import com.flutter.gbp.fcq.bet.SourceTag;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.BoolValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.FeaturesOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.SourceTagOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.WalletInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.changeinterms.ChangeInTermsOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.status.BetStatusOuterClass;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import javax.annotation.Nonnull;
import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass.CombinedPrice.CombinedPriceType.SGM;
import static com.ppb.platform.sb.fbp.proto.bet.changeinterms.ChangeInTermsOuterClass.ChangeInTerms.Reason;

@Mapper(implementationName = "BetConverterImpl", componentModel = "spring")
public abstract class BetConverter {

    private static final List<Reason> BLACKLISTED_CHANGE_IN_TERMS_REASONS = List.of(
            Reason.EDGE_EACHWAY,
            Reason.SAME_GAME_MULTIS,
            Reason.ORIGINAL_VALUES);

    @Resource(name = "fbpBetQuotingDataConverter")
    private BetQuotingDataConverter betQuotingDataConverter;
    @Resource(name = "fbpOutcomeBasedLegConverter")
    private OutcomeBasedLegConverter outcomeBasedLegConverter;
    @Resource(name = "fbpMarketBasedLegConverter")
    private MarketBasedLegConverter marketBasedLegConverter;
    @Resource(name = "javaValueToProtoValueConverter")
    private JavaValueToProtoValueConverter javaValueToProtoValueConverter;
    @Resource(name = "fbpOddsConverter")
    private OddsConverter oddsConverter;
    @Resource(name = "fbpBetStatusConverter")
    private BetStatusConverter betStatusConverter;
    @Resource(name = "fbpEligibleJurisdictionConverter")
    private EligibleJurisdictionConverter eligibleJurisdictionConverter;
    @Resource(name = "fbpFundingTypeConverter")
    private FundingTypeConverter fundingTypeConverter;
    @Resource(name = "fbpAccaFreezeEnricher")
    private AccaFreezeEnricher accaFreezeEnricher;
    @Resource(name = "fbpBetContextEnricherChain")
    private BetContextEnricherChain betContextEnricherChain;
    @Resource(name = "fbpRiskContextConverter")
    private RiskContextConverter riskContextConverter;

    @Mapping(target = "betQuotingData", source = "bet.bet", qualifiedByName = "convertBetQuotingData")
    @Mapping(target = "isSettled", source = "bet.bet.isSettled")
    @Mapping(target = "fundingType", source = "bet.bet.walletInfoList", qualifiedByName = "convertFundingType")
    @Mapping(target = "potentialWin", source = "bet.bet.potentialWinInfo.currentPotentialWin", qualifiedByName = "convertDoubleValue")
    @Mapping(target = "originalPotentialWin",
            source = "bet.bet.potentialWinInfo.marginatedPotentialWin", qualifiedByName = "convertDoubleValue")
    @Mapping(target = "betReceiptId", source = "bet.bet.betReceiptId")
    @Mapping(target = "status", source = "bet.bet.status", qualifiedByName = "convertBetStatus")
    @Mapping(target = "placedDate", source = "bet.bet.placedDate", qualifiedByName = "convertPlacedDate")
    @Mapping(target = "betPrice", source = "bet.bet.combinedPrice.decimal")
    @Mapping(target = "price", source = "bet.bet.combinedPrice", qualifiedByName = "convertCombinedPriceToOdds")
    @Mapping(target = "cashedOutAmount", source = "bet.bet.changeInTermsList", qualifiedByName = "convertCashedOutAmount")
    @Mapping(target = "currentWinningsWithoutBogCap", source = "bet.bet.currentWinnings.currentWinningsWithoutBogCap")
    @Mapping(target = "currentWinningsExcludingBog", source = "bet.bet.currentWinnings.currentWinningsExcludingBog")
    @Mapping(target = "cashoutPending", source = "bet.bet.cashoutPending")
    @Mapping(target = "hasPriceBoostedChangeInTerms", source = "bet.bet.changeInTermsList", qualifiedByName =
            "convertChangeInTermsBoostedPrice")
    @Mapping(target = "isTeaser", source = "bet.bet.changeInTermsList", qualifiedByName = "convertIsTeaser")
    @Mapping(target = "isSameGameMultis", source = "bet.bet", qualifiedByName = "convertIsSameGameMultis")
    @Mapping(target = "currency", source = "bet.currency")
    @Mapping(target = "platform", constant = "ONLINE")
    @Mapping(target = "retailInfo", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "allFields", ignore = true)
    @Mapping(target = "betVersion", source = "betVersion", qualifiedByName = "convertBetVersion")
    @Mapping(target = "sourceTag", source = "bet.sourceTag", qualifiedByName = "convertSourceTag")
    @Mapping(target = "hasEarlyWinReward", source = "bet.bet.features", qualifiedByName = "convertHasEarlyWinReward")
    @Mapping(target = "riskContext", ignore = true)
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

    @Named("convertCashedOutAmount")
    protected double convertCashedOutAmount(List<ChangeInTermsOuterClass.ChangeInTerms> changeInTermsList) {
        return changeInTermsList.stream()
                .filter(changeInTerms -> !BLACKLISTED_CHANGE_IN_TERMS_REASONS.contains(changeInTerms.getReasonCode())
                        && changeInTerms.hasCashoutValue())
                .map(ChangeInTermsOuterClass.ChangeInTerms::getCashoutValue)
                .mapToDouble(DoubleValue::getValue)
                .sum();
    }

    @Named("convertChangeInTermsBoostedPrice")
    protected BoolValue convertChangeInTermsBoostedPrice(List<ChangeInTermsOuterClass.ChangeInTerms> changeInTermsList) {
        return BoolValue.of(hasSgmChangeInTermsForReason(changeInTermsList, Reason.BOOST));
    }

    @Named("convertIsSameGameMultis")
    protected boolean convertIsSameGameMultis(BetOuterClass.Bet bet) {
        boolean hasSgmChangeInTerms = hasSgmChangeInTermsForReason(bet.getChangeInTermsList(), Reason.SAME_GAME_MULTIS);
        boolean hasSgmCombinedPrice = bet.hasCombinedPrice() && SGM.equals(bet.getCombinedPrice().getType());
        return hasSgmChangeInTerms || hasSgmCombinedPrice;
    }

    @Named("convertIsTeaser")
    protected boolean convertIsTeaser(List<ChangeInTermsOuterClass.ChangeInTerms> changeInTermsList) {
        return changeInTermsList.stream()
                .map(ChangeInTermsOuterClass.ChangeInTerms::getReasonCode)
                .anyMatch(reason -> reason == Reason.TEASER);
    }

    @Named("convertCombinedPriceToOdds")
    protected Odds convertCombinedPriceToOdds(CombinedPriceOuterClass.CombinedPrice combinedPrice) {
        return oddsConverter.convert(combinedPrice);
    }

    @Named("convertDoubleValue")
    protected DoubleValue convertDoubleValue(double doubleValue) {
        return javaValueToProtoValueConverter.convertToDoubleValue(doubleValue);
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
    protected void enrichWithAccaFreeze(BetDomainEventOuterClass.BetDomainEvent source, @MappingTarget Bet.Builder bet) {
        if (source.hasBet()) {
            accaFreezeEnricher.enrich(bet, source.getBet().getChangeInTermsList());
        }
    }

    @AfterMapping
    protected void enrichWithBetContext(BetDomainEventOuterClass.BetDomainEvent source, @MappingTarget Bet.Builder target) {
        if (source.hasBet()) {
            betContextEnricherChain.enrich(source, target);
        }
    }

    @AfterMapping
    protected void convertRiskContext(BetDomainEventOuterClass.BetDomainEvent source, @MappingTarget Bet.Builder target) {
        if (source.hasRiskContext()) {
            riskContextConverter.convert(source.getRiskContext())
                .ifPresent(target::setRiskContext);
        }
    }

    private List<Leg> convertLegs(BetOuterClass.Bet bet) {
        Set<String> superSubEligibleRampIds = getSuperSubRampSelectionIdsSet(bet);
        return bet.getLegsList().stream()
                .map(leg -> convertLeg(leg, superSubEligibleRampIds, bet.getBetId()))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Converts a single FBP leg into the CBD legs it represents, using the converter for its leg type.
     * <p>
     * An outcome-based leg is a self-contained multiple choice group and becomes exactly one CBD Leg, while a leg
     * of any other type becomes one CBD Leg per FBP Part. Only the latter can be SuperSub eligible, so the SuperSub
     * eligible ramp ids are only of interest to the market-based converter.
     *
     * @param leg the FBP leg to convert
     * @param superSubEligibleRampIds the bet's SuperSub eligible selection ramp ids
     * @param betId the id of the bet being converted; the FBP leg carries no bet identifier and leg numbers are bet
     *              scoped, so the leg converters need it to make anything they log traceable back to a bet
     * @return the CBD legs converted from this FBP leg
     */
    private List<Leg> convertLeg(LegOuterClass.Leg leg, Set<String> superSubEligibleRampIds, String betId) {
        if (LegOuterClass.Leg.LegType.OUTCOME_BASED == leg.getLegType()) {
            return outcomeBasedLegConverter.convert(leg, betId);
        }
        return marketBasedLegConverter.convert(leg, superSubEligibleRampIds, betId);
    }

    @Nonnull
    private Set<String> getSuperSubRampSelectionIdsSet(@Nonnull BetOuterClass.Bet bet) {
        return Optional.of(bet)
                .map(BetOuterClass.Bet::getFeatures)
                .map(FeaturesOuterClass.Features::getSuperSub)
                .map(FeaturesOuterClass.SuperSub::getSelectionRampIdsList)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
    }

    @Named("convertSourceTag")
    protected SourceTag convertSourceTag(@Nonnull SourceTagOuterClass.SourceTag sourceTag) {
        switch (sourceTag) {
            case GBP:
                return SourceTag.GBP;
            case OBB:
                return SourceTag.OBB;
            default:
                return SourceTag.OPENBET;
        }
    }

    @Named("convertBetVersion")
    protected StringValue convertBetVersion(StringValue betVersion) {
        return StringValue.newBuilder(betVersion).build();
    }

    @Named("convertHasEarlyWinReward")
    protected boolean convertHasEarlyWinReward(FeaturesOuterClass.Features features) {
        return features != null && features.hasEarlyWin();
    }

    private boolean hasSgmChangeInTermsForReason(List<ChangeInTermsOuterClass.ChangeInTerms> changeInTermsList, Reason reason) {
        return changeInTermsList
                .stream()
                .map(ChangeInTermsOuterClass.ChangeInTerms::getReasonCode)
                .anyMatch(reason::equals);
    }
}
