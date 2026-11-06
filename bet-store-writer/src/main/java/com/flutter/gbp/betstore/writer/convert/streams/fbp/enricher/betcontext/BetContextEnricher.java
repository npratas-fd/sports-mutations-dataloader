package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.fcq.bet.Bet;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;

/**
 * Enriches an ongoing FCQ domain bet builder with additional information from
 * the bet context.
 * <p>
 * Implementations of this interface are responsible for extracting specific
 * data from the decoded {@link BetContext} and applying it to the target
 * {@link Bet.Builder}, Leveraging additional information present in the source
 * {@link BetOuterClass.Bet}.
 * </p>
 * <p>
 * Enrichers are typically composed in a chain via {@link BetContextEnricherChain},
 * which orchestrates the execution of multiple enrichers in sequence.
 * </p>
 *
 * @see BetContextEnricherChain
 * @see BetContext
 */
public interface BetContextEnricher {
    /**
     * Enriches the target bet builder with information from the bet context.
     * <p>
     * This method assumes that a valid {@link BetContext} has successfully
     * been decoded from the source bet. Implementations should extract relevant
     * data from the context and the source bet and apply it to the target builder.
     * </p>
     *
     * @param source the source FBP protobuf bet containing the raw bet data
     * @param betContext the decoded bet context containing additional bet information
     * @param target the FCQ bet builder to be enriched with context data
     */
    void enrich(BetOuterClass.Bet source, BetContext betContext, Bet.Builder target);
}
