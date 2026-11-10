package com.flutter.gbp.sportsmutationsdataloader.writer.event;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.bet.BetConverter;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.EntityOuterClass;
import com.ppb.platform.sb.retail.proto.bet.ProductOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass.BetDomainEvent;
import java.util.Optional;

/**
 * Adapts an ABP (retail) {@link BetDomainEvent} to a {@link SourceEvent}. Virtual detection is the same as FCQ
 * {@code AbpEventProcessor}.
 *
 * <p>Retail bets have no source tag ({@link SourceEvent#UNSPECIFIED_SOURCE_TAG}) and no {@code OUTCOME_BASED} leg
 * type, so {@code hasObpLeg} only reflects parts with a {@code MISC} id. It is informational: as in FCQ, the OBP
 * toggle does not apply to ABP.
 */
public class AbpEventAdapter {

    private static final String MISC_IDENTIFIER = "MISC";

    private final BetConverter betConverter;

    /**
     * @param betConverter ABP stream converter ({@code abpBetConverter})
     */
    public AbpEventAdapter(BetConverter betConverter) {
        checkArgument(betConverter != null, "betConverter must be provided");
        this.betConverter = betConverter;
    }

    /**
     * @param event ABP bet event
     * @return the source event, or empty if the event has no bet
     */
    public Optional<SourceEvent> adapt(BetDomainEvent event) {
        if (event == null || !event.hasBet()) {
            return Optional.empty();
        }
        BetOuterClass.Bet bet = event.getBet();
        return Optional.of(new SourceEvent(
                SourceEvent.ABP,
                bet.getAccountId(),
                bet.getBetId(),
                bet.getIsSettled(),
                bet.getProduct() == ProductOuterClass.Product.VIRTUAL_SPORTS,
                hasAnyMiscPart(bet),
                event.getContext().getContextMap(),
                SourceEvent.UNSPECIFIED_SOURCE_TAG,
                () -> betConverter.convert(event)));
    }

    private static boolean hasAnyMiscPart(BetOuterClass.Bet bet) {
        return bet.getLegsList().stream().anyMatch(leg ->
                leg.getPartList().stream().anyMatch(part -> getMiscFeedId(part).isPresent()));
    }

    private static Optional<String> getMiscFeedId(PartOuterClass.Part part) {
        return Optional.ofNullable(part)
                .map(PartOuterClass.Part::getSelectionInfo)
                .map(PartOuterClass.SelectionInfo::getEntity)
                .map(EntityOuterClass.Entity::getIdsMap)
                .map(ids -> ids.get(MISC_IDENTIFIER));
    }
}
