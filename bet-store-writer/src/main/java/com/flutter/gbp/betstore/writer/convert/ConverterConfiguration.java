package com.flutter.gbp.betstore.writer.convert;

import com.flutter.gbp.betcontext.BetContextMessageDecoder;
import com.flutter.gbp.betcontext.factory.BetContextMessageDecoderFactory;
import com.flutter.gbp.betstore.writer.config.WriterProperties;
import com.flutter.gbp.betstore.writer.convert.streams.BetClassConverter;
import com.flutter.gbp.betstore.writer.convert.streams.BetTypeConverter;
import com.flutter.gbp.betstore.writer.convert.streams.LegStatusConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.RiskContextConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.AccaFreezeEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.AppliedRewardsContextEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.BetContextEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.BetContextEnricherChain;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.BetLegProbabilityEnricher;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.FbpFractionalPriceWriter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.MarketBasedLegConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.MarketBasedLegConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.MarketBasedLegQuotingDataConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.MarketBasedLegQuotingDataConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.OutcomeBasedLegConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.OutcomeBasedLegConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.OutcomeBasedLegQuotingDataConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.OutcomeBasedLegQuotingDataConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.PartConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.ChoiceConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.ChoiceConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.EventHierarchyConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.EventHierarchyConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.OutcomeBasedDetailsConverter;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.OutcomeBasedDetailsConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree.ChoiceTreeIndexer;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree.ChoiceTreeRules;
import com.flutter.gbp.betstore.writer.convert.streams.fbp.outcome.details.tree.ChoiceTreeValidator;
import com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegConverterImpl;
import com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegQuotingDataConverter;
import com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegQuotingDataConverterImpl;
import com.flutter.gbp.betstore.writer.convert.utils.BetContextDecoder;
import com.flutter.gbp.betstore.writer.convert.utils.JavaValueToProtoValueConverter;
import com.flutter.gbp.betstore.writer.convert.utils.SgmGroupPriceConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stream converter beans, ported 1:1 from FCQ {@code service-converters.xml} (the {@code streams} beans of the
 * {@code cassandra} profile plus {@code sgmGroupPriceConverter}) and the {@code betContextMessageDecoder} bean of
 * FCQ {@code service-beans.xml}.
 *
 * <p>Bean names are the FCQ bean ids: the copied MapStruct mappers inject their collaborators by name with
 * {@code @Resource}. FCQ feature toggles are replaced by {@link BooleanSupplier} beans backed by
 * {@link WriterProperties#toggles()}; their bean names are the FCQ toggle ids.
 *
 * <p>Classes with the same simple name in the FBP and ABP packages are written fully qualified.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WriterProperties.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class ConverterConfiguration {

    static final String ROUND_START_PRICE_HALF_UP_TOGGLE = "fcq.featureToggle.enable.roundStartPriceHalfUp";
    static final String MARKET_BASED_OBP_BETS_TOGGLE = "fcq.featureToggle.enable.market.based.obp.bets";

    // ---------------------------------------------------------------- toggles

    @Bean(ROUND_START_PRICE_HALF_UP_TOGGLE)
    BooleanSupplier roundStartPriceHalfUpToggle(WriterProperties properties) {
        boolean enabled = properties.toggles().roundStartPriceHalfUp();
        return () -> enabled;
    }

    @Bean(MARKET_BASED_OBP_BETS_TOGGLE)
    BooleanSupplier marketBasedObpBetsToggle(WriterProperties properties) {
        boolean enabled = properties.toggles().marketBasedObpBets();
        return () -> enabled;
    }

    // ---------------------------------------------------------------- common converters

    @Bean
    SgmGroupPriceConverter sgmGroupPriceConverter(
            @Qualifier(ROUND_START_PRICE_HALF_UP_TOGGLE) BooleanSupplier roundStartPriceHalfUpToggle) {
        return new SgmGroupPriceConverter(roundStartPriceHalfUpToggle);
    }

    @Bean
    JavaValueToProtoValueConverter javaValueToProtoValueConverter() {
        return new JavaValueToProtoValueConverter();
    }

    @Bean
    BetContextMessageDecoder betContextMessageDecoder() {
        return BetContextMessageDecoderFactory.getDecoder();
    }

    // ---------------------------------------------------------------- FBP converters

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetConverter fbpBetConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetConverterImpl();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetQuotingDataConverter fbpBetQuotingDataConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetQuotingDataConverterImpl();
    }

    @Bean
    BetClassConverter fbpBetClassConverter() {
        return new BetClassConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetStatusConverter fbpBetStatusConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetStatusConverter();
    }

    @Bean
    BetTypeConverter fbpBetTypeConverter() {
        return new BetTypeConverter();
    }

    @Bean
    OutcomeBasedLegConverter fbpOutcomeBasedLegConverter() {
        return new OutcomeBasedLegConverterImpl();
    }

    @Bean
    MarketBasedLegConverter fbpMarketBasedLegConverter() {
        return new MarketBasedLegConverterImpl();
    }

    @Bean
    OutcomeBasedLegQuotingDataConverter fbpOutcomeBasedLegQuotingDataConverter() {
        return new OutcomeBasedLegQuotingDataConverterImpl();
    }

    @Bean
    MarketBasedLegQuotingDataConverter fbpMarketBasedLegQuotingDataConverter() {
        return new MarketBasedLegQuotingDataConverterImpl();
    }

    @Bean
    PartConverter fbpPartConverter() {
        return new PartConverter();
    }

    @Bean
    LegStatusConverter fbpLegStatusConverter() {
        return new LegStatusConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.LegTypeConverter fbpLegTypeConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.LegTypeConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.StartPriceConverter fbpStartPriceConverter(
            @Qualifier(ROUND_START_PRICE_HALF_UP_TOGGLE) BooleanSupplier roundStartPriceHalfUpToggle) {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.StartPriceConverter(roundStartPriceHalfUpToggle);
    }

    @Bean
    FbpFractionalPriceWriter fbpFractionalPriceWriter(
            @Qualifier("fbpStartPriceConverter")
            com.flutter.gbp.betstore.writer.convert.streams.fbp.leg.StartPriceConverter startPriceConverter,
            @Qualifier("fbpPriceTypeConverter")
            com.flutter.gbp.betstore.writer.convert.streams.fbp.PriceTypeConverter priceTypeConverter) {
        return new FbpFractionalPriceWriter(startPriceConverter, priceTypeConverter);
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.EligibleJurisdictionConverter fbpEligibleJurisdictionConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.EligibleJurisdictionConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.FundingTypeConverter fbpFundingTypeConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.FundingTypeConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.OddsConverter fbpOddsConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.OddsConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.FractionalOddsConverter fbpFractionalOddsConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.FractionalOddsConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.PriceTypeConverter fbpPriceTypeConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.PriceTypeConverter();
    }

    @Bean
    ChoiceConverter fbpChoiceConverter() {
        return new ChoiceConverterImpl();
    }

    @Bean
    ChoiceTreeIndexer choiceTreeIndexer() {
        return new ChoiceTreeIndexer();
    }

    @Bean
    ChoiceTreeRules choiceTreeRules() {
        return new ChoiceTreeRules();
    }

    @Bean
    ChoiceTreeValidator choiceTreeValidator(ChoiceTreeIndexer choiceTreeIndexer, ChoiceTreeRules choiceTreeRules) {
        return new ChoiceTreeValidator(choiceTreeIndexer, choiceTreeRules);
    }

    @Bean
    EventHierarchyConverter fbpEventHierarchyConverter() {
        return new EventHierarchyConverterImpl();
    }

    @Bean
    OutcomeBasedDetailsConverter fbpOutcomeBasedDetailsConverter() {
        return new OutcomeBasedDetailsConverterImpl();
    }

    @Bean
    AccaFreezeEnricher fbpAccaFreezeEnricher() {
        return new AccaFreezeEnricher();
    }

    @Bean
    RiskContextConverter fbpRiskContextConverter() {
        return new RiskContextConverter();
    }

    @Bean
    BetContextDecoder fbpBetContextDecoder(BetContextMessageDecoder betContextMessageDecoder) {
        return new BetContextDecoder(betContextMessageDecoder);
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.SgmEnricher fbpSgmEnricher(
            SgmGroupPriceConverter sgmGroupPriceConverter) {
        return new com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext.SgmEnricher(sgmGroupPriceConverter);
    }

    @Bean
    BetLegProbabilityEnricher fbpBetLegProbabilityEnricher() {
        return new BetLegProbabilityEnricher();
    }

    @Bean
    AppliedRewardsContextEnricher fbpAppliedRewardContextEnricher() {
        return new AppliedRewardsContextEnricher();
    }

    @Bean
    List<BetContextEnricher> fbpBetContextDependentEnrichers(
            @Qualifier("fbpSgmEnricher") BetContextEnricher fbpSgmEnricher,
            @Qualifier("fbpBetLegProbabilityEnricher") BetContextEnricher fbpBetLegProbabilityEnricher,
            @Qualifier("fbpAppliedRewardContextEnricher") BetContextEnricher fbpAppliedRewardContextEnricher) {
        return new ArrayList<>(List.of(fbpSgmEnricher, fbpBetLegProbabilityEnricher, fbpAppliedRewardContextEnricher));
    }

    @Bean
    BetContextEnricherChain fbpBetContextEnricherChain(
            @Qualifier("fbpBetContextDecoder") BetContextDecoder betContextDecoder,
            @Qualifier("fbpBetContextDependentEnrichers") List<BetContextEnricher> enrichers) {
        return new BetContextEnricherChain(betContextDecoder, enrichers);
    }

    // ---------------------------------------------------------------- ABP converters

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetConverter abpBetConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetConverterImpl();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetQuotingDataConverter abpBetQuotingDataConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetQuotingDataConverterImpl();
    }

    @Bean
    BetClassConverter abpBetClassConverter() {
        return new BetClassConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetStatusConverter abpBetStatusConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetStatusConverter();
    }

    @Bean
    BetTypeConverter abpBetTypeConverter() {
        return new BetTypeConverter();
    }

    @Bean
    LegConverter abpLegConverter() {
        return new LegConverterImpl();
    }

    @Bean
    LegQuotingDataConverter abpLegQuotingDataConverter() {
        return new LegQuotingDataConverterImpl();
    }

    @Bean
    LegStatusConverter abpLegStatusConverter() {
        return new LegStatusConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegTypeConverter abpLegTypeConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.leg.LegTypeConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.leg.StartPriceConverter abpStartPriceConverter(
            @Qualifier(ROUND_START_PRICE_HALF_UP_TOGGLE) BooleanSupplier roundStartPriceHalfUpToggle) {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.leg.StartPriceConverter(roundStartPriceHalfUpToggle);
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.EligibleJurisdictionConverter abpEligibleJurisdictionConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.EligibleJurisdictionConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.FundingTypeConverter abpFundingTypeConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.FundingTypeConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.OddsConverter abpOddsConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.OddsConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.FractionalOddsConverter abpFractionalOddsConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.FractionalOddsConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.PriceTypeConverter abpPriceTypeConverter() {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.PriceTypeConverter();
    }

    @Bean
    com.flutter.gbp.betstore.writer.convert.streams.abp.SgmEnricher abpSgmEnricher(
            SgmGroupPriceConverter sgmGroupPriceConverter) {
        return new com.flutter.gbp.betstore.writer.convert.streams.abp.SgmEnricher(sgmGroupPriceConverter);
    }
}
