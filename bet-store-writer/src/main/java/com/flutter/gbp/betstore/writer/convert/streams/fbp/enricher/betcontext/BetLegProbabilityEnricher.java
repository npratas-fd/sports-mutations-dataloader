package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.domain.BetLegContext;
import com.flutter.gbp.betcontext.domain.LegContext;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.google.protobuf.DoubleValue;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Enriches FCQ domain {@link Leg} instances with placement probability data sourced from the
 * {@link BetContext}. The probability is matched per leg by leg number and stored on the leg
 * as an optional {@code placementProbability} field, used by the OBP cashout in-play quoting path.
 *
 * @see BetContextEnricher
 * @see BetContextEnricherChain
 */
public class BetLegProbabilityEnricher implements BetContextEnricher {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetLegProbabilityEnricher.class);

    @Override
    public void enrich(BetOuterClass.Bet source, BetContext betContext, Bet.Builder target) {
        Map<Integer, Double> probabilityByLegNumber = extractProbabilities(betContext);
        LOGGER.debug("operation=enrich, message='Extracted probabilities from BetContext', probabilityByLegNumber={}",
                probabilityByLegNumber);
        if (probabilityByLegNumber.isEmpty()) {
            return;
        }
        applyProbabilities(target, probabilityByLegNumber);
        warnOnUnmatched(source, target, probabilityByLegNumber);
    }

    private Map<Integer, Double> extractProbabilities(BetContext betContext) {
        return Optional.ofNullable(betContext)
                .flatMap(BetContext::legContext)
                .map(LegContext::betLegContext)
                .orElse(List.of())
                .stream()
                .filter(ctx -> ctx.betLegProbability().isPresent())
                .collect(toMap(BetLegContext::betLegNumber, ctx -> ctx.betLegProbability().orElseThrow()));
    }

    private void applyProbabilities(Bet.Builder target, Map<Integer, Double> probabilityByLegNumber) {
        List<Leg> enrichedLegs = target.getLegsList().stream()
                .map(leg -> enrichLeg(leg, probabilityByLegNumber))
                .collect(toList());
        target.clearLegs().addAllLegs(enrichedLegs);
    }

    private Leg enrichLeg(Leg leg, Map<Integer, Double> probabilityByLegNumber) {
        return Optional.ofNullable(probabilityByLegNumber.get(leg.getLegQuotingData().getLegNumber()))
                .map(probability -> Leg.newBuilder(leg).setPlacementProbability(DoubleValue.of(probability)).build())
                .orElse(leg);
    }

    private void warnOnUnmatched(BetOuterClass.Bet source, Bet.Builder target,
                                 Map<Integer, Double> probabilityByLegNumber) {
        long matchedCount = target.getLegsList().stream()
                .map(leg -> leg.getLegQuotingData().getLegNumber())
                .filter(probabilityByLegNumber::containsKey)
                .count();
        if (matchedCount < probabilityByLegNumber.size()) {
            LOGGER.warn("operation=enrich, message='Some BetLegContext entries could not be matched to a leg', "
                    + "betId={}, expectedMatches={}, actualMatches={}",
                    source.getBetId(), probabilityByLegNumber.size(), matchedCount);
        }
    }
}
