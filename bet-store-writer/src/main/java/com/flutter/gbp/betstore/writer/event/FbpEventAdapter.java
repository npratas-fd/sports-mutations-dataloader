package com.flutter.gbp.betstore.writer.event;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetConverter;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.ProductOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.SourceTagOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass.BetDomainEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adapts an FBP (online) {@link BetDomainEvent} to a {@link SourceEvent}. Virtual and OBP detection is the same as
 * FCQ {@code FbpEventProcessor}.
 */
public class FbpEventAdapter {

    private static final String MISC_IDENTIFIER = "MISC";

    private final BetConverter betConverter;

    /**
     * @param betConverter FBP stream converter ({@code fbpBetConverter})
     */
    public FbpEventAdapter(BetConverter betConverter) {
        checkArgument(betConverter != null, "betConverter must be provided");
        this.betConverter = betConverter;
    }

    /**
     * @param event FBP bet event
     * @return the source event, or empty if the event has no bet
     */
    public Optional<SourceEvent> adapt(BetDomainEvent event) {
        if (event == null || !event.hasBet()) {
            return Optional.empty();
        }
        BetOuterClass.Bet bet = event.getBet();
        return Optional.of(new SourceEvent(
                SourceEvent.FBP,
                bet.getAccountId(),
                bet.getBetId(),
                bet.getIsSettled(),
                bet.getProduct() == ProductOuterClass.Product.VIRTUAL_SPORTS,
                hasAnyObpLeg(bet),
                event.getContext().getContextMap(),
                sourceTag(bet.getSourceTag()),
                () -> betConverter.convert(event)));
    }

    private static String sourceTag(SourceTagOuterClass.SourceTag tag) {
        if (tag == SourceTagOuterClass.SourceTag.UNRECOGNIZED) {
            return SourceEvent.UNSPECIFIED_SOURCE_TAG;
        }
        return tag.name().toLowerCase(Locale.ROOT);
    }

    private static boolean hasAnyObpLeg(BetOuterClass.Bet bet) {
        return hasAnyOutcomeBasedObpLeg(bet.getLegsList()) || hasAnyMarketBasedObpLeg(bet.getLegsList());
    }

    private static boolean hasAnyMarketBasedObpLeg(List<LegOuterClass.Leg> legsList) {
        return legsList.stream().anyMatch(leg ->
                leg.getPartList().stream().anyMatch(part -> getMiscFeedId(part).isPresent()));
    }

    private static boolean hasAnyOutcomeBasedObpLeg(List<LegOuterClass.Leg> legsList) {
        return legsList.stream().anyMatch(leg -> LegOuterClass.Leg.LegType.OUTCOME_BASED == leg.getLegType());
    }

    private static Optional<String> getMiscFeedId(PartOuterClass.Part part) {
        return Optional.ofNullable(part)
                .map(PartOuterClass.Part::getSelectionInfo)
                .map(PartOuterClass.SelectionInfo::getEntity)
                .map(PartOuterClass.Entity::getIdsMap)
                .map(ids -> ids.get(MISC_IDENTIFIER));
    }
}
