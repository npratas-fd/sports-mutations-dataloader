package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.SgmGroupPriceConverter;
import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.domain.SameGameGroup;
import com.flutter.gbp.betcontext.domain.SgmContext;
import com.flutter.gbp.betcontext.domain.SgmSelection;
import com.flutter.gbp.betcontext.domain.SportsbookSelection;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.sgm.SgmGroup;
import com.flutter.gbp.fcq.bet.sgm.SgmGroupQuotingData;
import com.google.common.base.MoreObjects;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

public class SgmEnricher  implements BetContextEnricher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SgmEnricher.class);
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";
    private static final String RAMP_IDENTIFIER = "RAMP";
    private static final String BMS_IDENTIFIER = "OPENBET";
    private static final String ZERO_ID = "0";

    private final SgmGroupPriceConverter sgmGroupPriceConverter;

    public SgmEnricher(SgmGroupPriceConverter sgmGroupPriceConverter) {
        checkArgument(sgmGroupPriceConverter != null, "sgmGroupPriceConverter must be provided");
        this.sgmGroupPriceConverter = sgmGroupPriceConverter;
    }

    @Override
    public void enrich(BetOuterClass.Bet source, BetContext betContext, Bet.Builder target) {
        ofNullable(betContext)
                .flatMap(BetContext::sgmContext)
                .ifPresent(sgmContext -> {
                    enrichSgmGroups(target, sgmContext);
                    enrichSgmLegs(source, target, sgmContext);
                    if (isSimpleSgm(target)) {
                        enrichSingleSgmGroup(source, target);
                    }
                });
    }

    private void enrichSgmGroups(Bet.Builder betBuilder, SgmContext sgmContextValue) {
        Set<SgmGroup> sgmGroups = sgmContextValue.selectionsGroup().values().stream()
                .map(this::convertToSgmGroup) // Convert each SameGameGroup to ConvertedObject
                .sorted(Comparator.comparing(group -> group.getSgmGroupQuotingData().getRef().getValue()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        betBuilder.addAllSgmGroups(sgmGroups);
    }

    /**
     * Checks if a bet is a simple SGM (i.e. it has only one SGM group and zero non-SGM legs)
     *
     * @param betBuilder The bet builder to check
     * @return true if the bet is a simple SGM, false otherwise
     */
    private boolean isSimpleSgm(Bet.Builder betBuilder) {
        return betBuilder.getSgmGroupsList().size() == 1 &&
                betBuilder.getLegsList().stream().noneMatch(leg -> leg.getLegQuotingData().getGroupRefList().isEmpty());
    }

    /**
     * Adds price information to the SGM group because for simple SGM bets this information is not filled and must be
     * read at bet price level.
     *
     * @param bet        The original bet
     * @param betBuilder The bet builder to enrich
     */
    private void enrichSingleSgmGroup(BetOuterClass.Bet bet, Bet.Builder betBuilder) {
        double betPrice = betBuilder.getBetPrice();
        Double originalPrice = null;
        if (bet.hasCombinedPrice() && bet.getCombinedPrice().hasOriginalDecimal()) {
            originalPrice = Optional.of(bet.getCombinedPrice())
                    .map(CombinedPriceOuterClass.CombinedPrice::getOriginalDecimal)
                    .map(DoubleValue::getValue)
                    .orElse(null);
        }

        Optional<SgmGroup> existingSgmGroup = betBuilder.getSgmGroupsList().stream().findFirst();
        if (existingSgmGroup.isPresent()) {
            SgmGroupQuotingData.Builder sgmGroupQuotingDataBuilder = SgmGroupQuotingData
                    .newBuilder(existingSgmGroup.get().getSgmGroupQuotingData())
                    .setPrice(DoubleValue.of(betPrice));
            if (originalPrice != null) {
                sgmGroupQuotingDataBuilder.setOriginalPrice(DoubleValue.of(originalPrice));
            }
            SgmGroup newSgmGroup = SgmGroup.newBuilder(existingSgmGroup.get())
                    .setSgmGroupQuotingData(sgmGroupQuotingDataBuilder.build())
                    .build();

            betBuilder.clearSgmGroups().addSgmGroups(newSgmGroup);
        }
    }

    /**
     * Checks if the leg has the required IDs to be matched with an SGM selection.
     *
     * @param leg The leg to check.
     * @return true if the leg has the required IDs, false otherwise.
     */
    private boolean hasRequiredIds(LegOuterClass.Leg leg) {
        if (leg == null || leg.getPartCount() == 0) {
            return false;
        }
        PartOuterClass.Part part = leg.getPart(0);
        Map<String, String> selectionIds = part.getSelectionInfo().getEntity().getIdsMap();
        Map<String, String> marketIds = part.getMarketInfo().getEntity().getIdsMap();
        String sportexSelectionId = getValidSelectionId(selectionIds, SPORTEX_IDENTIFIER);
        String bmsSelectionId = getValidSelectionId(selectionIds, BMS_IDENTIFIER);

        return (sportexSelectionId != null || bmsSelectionId != null)
                && marketIds.containsKey(SPORTSBOOK_IDENTIFIER);
    }

    private void enrichSgmLegs(BetOuterClass.Bet bet,
                               Bet.Builder betBuilder, SgmContext sgmContext) {

        Map<LegOuterClass.Leg, SgmSelection> legSgmSelectionMap = indexLegs(bet, sgmContext);

        if (!legSgmSelectionMap.values().containsAll(sgmContext.selectionsGroup().keySet())) {
            LOGGER.warn("operation=enrich, message='Cannot enrich SGM leg groups', betId={} ", bet.getBetId());
            return;
        }

        legSgmSelectionMap.entrySet().stream()
                // Filtering out non sgm parts
                .filter(entry -> sgmContext.selectionsGroup().containsKey(entry.getValue()))
                // for each leg that we know that belongs to an SGM group we will enrich it with group information
                .forEach(entry -> enrichLegGroupRef(entry.getKey(), betBuilder, sgmContext.selectionsGroup().get(entry.getValue())));
    }

    private void enrichLegGroupRef(LegOuterClass.Leg leg, Bet.Builder betBuilder, SameGameGroup sameGameGroup) {

        // Obtain legs to modify and create a map with them by their index
        Map<Integer, Leg> legsMod = IntStream.range(0, betBuilder.getLegsCount())
                .filter(i -> betBuilder.getLegs(i).getLegQuotingData().getLegNumber() == leg.getLegNumber())
                .mapToObj(i -> {
                    Leg legToModify = betBuilder.getLegs(i);
                    Leg modifiedLeg = Leg.newBuilder(legToModify)
                            .setLegQuotingData(LegQuotingData.newBuilder(legToModify.getLegQuotingData())
                                    .addGroupRef(StringValue.of(sameGameGroup.groupId()))
                                    .build())
                            .build();
                    return Map.entry(i, modifiedLeg);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Replace the original legs in betBuilder
        legsMod.forEach(betBuilder::setLegs);
    }

    private Map<LegOuterClass.Leg, SgmSelection> indexLegs(BetOuterClass.Bet bet, SgmContext sgmContext) {

        boolean isSelectionRampId = sgmContext.selectionsGroup().keySet().stream()
                .anyMatch(sgmSelection -> sgmSelection.rampSelection().isPresent());

        if (isSelectionRampId) {
            return indexLegsByRampId(bet, sgmContext);
        } else {
            return indexLegsBySportsbookIds(bet, sgmContext);
        }
    }

    private Map<LegOuterClass.Leg, SgmSelection> indexLegsByRampId(BetOuterClass.Bet bet, SgmContext sgmContext) {

        Map<LegOuterClass.Leg, String> partBySelection = bet.getLegsList().stream()
                .filter(leg -> getSelectionRampId(leg.getPart(0)) != null)
                .collect(toMap(leg -> leg, leg -> getSelectionRampId(leg.getPart(0))));

        return partBySelection.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> sgmContext.selectionsGroup().keySet().stream()
                        .filter(sgmSelection -> sgmSelection.rampSelection().isPresent()
                                && sgmSelection.rampSelection().get().selectionRampId().equals(entry.getValue()))
                        .map(sgmSelection -> Map.entry(entry.getKey(), sgmSelection)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<LegOuterClass.Leg, SgmSelection> indexLegsBySportsbookIds(BetOuterClass.Bet bet, SgmContext sgmContext) {
        List<LegOuterClass.Leg> validLegs = bet.getLegsList().stream()
                .filter(this::hasRequiredIds)
                .collect(Collectors.toList());

        return sgmContext.selectionsGroup().keySet().stream()
                .map(sgmSelection -> linkSgmSelectionWithLeg(sgmSelection, validLegs))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing, HashMap::new));
    }

    private Optional<Map.Entry<LegOuterClass.Leg, SgmSelection>> linkSgmSelectionWithLeg(SgmSelection sgmSelection,
        List<LegOuterClass.Leg> legs) {
        Optional<Map.Entry<LegOuterClass.Leg, SgmSelection>> opt = legs.stream()
                .filter(leg -> sgmSelection.sportsbookSelection().isPresent()
                        && sgmSelectionMatchesLeg(sgmSelection.sportsbookSelection().get(), leg))
                .map(leg -> Map.entry(leg, sgmSelection)).findFirst();

        if (opt.isEmpty()) {
            String marketId = sgmSelection.sportsbookSelection().map(SportsbookSelection::marketId).orElse("");
            String selectionId = sgmSelection.sportsbookSelection().map(SportsbookSelection::selectionId).orElse("");
            LOGGER.warn("operation=enrich, message='Cannot enrich SGM selection', marketId={}, selectionId={}",
                    marketId, selectionId);
        }
        return opt;
    }

    private boolean sgmSelectionMatchesLeg(SportsbookSelection sgmSelection, LegOuterClass.Leg leg) {
        String sgmSelectionMarketId = sgmSelection.marketId();
        String sgmSelectionSelectionId = sgmSelection.selectionId();
        PartOuterClass.Part part = leg.getPart(0);
        String legMarketId = part.getMarketInfo().getEntity().getIdsMap().get(SPORTSBOOK_IDENTIFIER);
        String legSelectionId = MoreObjects.firstNonNull(
                getValidSelectionId(part.getSelectionInfo().getEntity().getIdsMap(), SPORTEX_IDENTIFIER),
                getValidSelectionId(part.getSelectionInfo().getEntity().getIdsMap(), BMS_IDENTIFIER));

        return sgmSelectionMarketId.equals(legMarketId) && sgmSelectionSelectionId.equals(legSelectionId);
    }

    @Nullable
    private String getValidSelectionId(Map<String, String> idsMap, String key) {
        String id = idsMap.get(key);
        return ZERO_ID.equals(id) ? null : id;
    }

    private String getSelectionRampId(PartOuterClass.Part part) {
        return part.getSelectionInfo().getEntity().getIdsMap().get(RAMP_IDENTIFIER);
    }

    private SgmGroup convertToSgmGroup(SameGameGroup sameGameGroup) {
        SgmGroup.Builder sgmGroupBuilder = SgmGroup.newBuilder();
        SgmGroupQuotingData.Builder sgmGroupQuotingDataBuilder = SgmGroupQuotingData.newBuilder();

        sgmGroupQuotingDataBuilder.setRef(StringValue.of(sameGameGroup.groupId()));
        sameGameGroup.price()
                .map(price -> sgmGroupPriceConverter.convertSgmGroupPrice(price.decimal()))
                .ifPresent(sgmGroupQuotingDataBuilder::setPrice);

        sgmGroupBuilder.setSgmGroupQuotingData(sgmGroupQuotingDataBuilder.build());

        sameGameGroup.probability().ifPresent(
                probability -> sgmGroupBuilder.setPlacementProbability(DoubleValue.of(probability))
        );

        return sgmGroupBuilder.build();
    }
}
