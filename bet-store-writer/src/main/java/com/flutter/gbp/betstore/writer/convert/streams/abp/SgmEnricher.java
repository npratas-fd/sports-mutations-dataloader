package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.betstore.writer.convert.utils.SgmGroupPriceConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.sgm.SgmGroup;
import com.flutter.gbp.fcq.bet.sgm.SgmGroupQuotingData;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.FeaturesOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toMap;

public class SgmEnricher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SgmEnricher.class);
    private static final String RAMP_IDENTIFIER = "RAMP";

    private final SgmGroupPriceConverter sgmGroupPriceConverter;

    public SgmEnricher(SgmGroupPriceConverter sgmGroupPriceConverter) {
        checkArgument(sgmGroupPriceConverter != null, "sgmGroupPriceConverter must be provided");
        this.sgmGroupPriceConverter = sgmGroupPriceConverter;
    }

    public void enrichWithSgmData(BetOuterClass.Bet bet, Bet.Builder betBuilder) {
        Optional.of(bet)
                .map(BetOuterClass.Bet::getFeatures)
                .map(FeaturesOuterClass.Features::getSgpp)
                .map(FeaturesOuterClass.SGPPContext::getGroupsList)
                .ifPresent(groupsList -> {
                    enrichSgmGroups(betBuilder, groupsList);
                    enrichSgmLegs(bet, betBuilder, groupsList);
                });
    }

    private void enrichSgmGroups(Bet.Builder bet, List<FeaturesOuterClass.Group> groupList) {
        List<SgmGroup> sgmGroups = groupList.stream()
                .map(this::convertToSgmGroup) // Convert each FeaturesOuterClass.Group to SgmGroup
                .sorted(Comparator.comparing(group -> group.getSgmGroupQuotingData().getRef().getValue()))
                .collect(Collectors.toList());

        bet.addAllSgmGroups(sgmGroups);
    }

    private void enrichSgmLegs(BetOuterClass.Bet bet,
                               Bet.Builder betBuilder, List<FeaturesOuterClass.Group> groupList) {

        Map<LegOuterClass.Leg, FeaturesOuterClass.Group> legSgmGroupMap = indexLegs(bet, groupList);

        if (!legSgmGroupMap.values().containsAll(groupList)) {
            LOGGER.warn("operation=enrich, message='Cannot enrich SGM leg groups', betId={} ", bet.getBetId());
            return;
        }

        legSgmGroupMap.entrySet().stream()
                // Filtering out non sgm parts
                .filter(entry -> groupList.contains(entry.getValue()))
                // for each leg that we know that belongs to an SGM group we will enrich it with group information
                .forEach(entry -> enrichLegGroupRef(entry.getKey(), betBuilder, entry.getValue()));
    }

    private void enrichLegGroupRef(LegOuterClass.Leg leg, Bet.Builder betBuilder, FeaturesOuterClass.Group group) {
        // Obtain legs to modify and create a map with them by their index
        Map<Integer, Leg> legsMod = IntStream.range(0, betBuilder.getLegsCount())
                .filter(i -> betBuilder.getLegs(i).getLegQuotingData().getLegNumber() == leg.getLegNumber())
                .mapToObj(i -> {
                    Leg legToModify = betBuilder.getLegs(i);
                    Leg modifiedLeg = Leg.newBuilder(legToModify)
                            .setLegQuotingData(LegQuotingData.newBuilder(legToModify.getLegQuotingData())
                                    .addGroupRef(StringValue.of(String.valueOf(group.getGroupId())))
                                    .build())
                            .build();
                    return Map.entry(i, modifiedLeg);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Replace the original legs in betBuilder
        legsMod.forEach(betBuilder::setLegs);
    }

    private Map<LegOuterClass.Leg, FeaturesOuterClass.Group> indexLegs(BetOuterClass.Bet bet, List<FeaturesOuterClass.Group> groupList) {

        Map<LegOuterClass.Leg, String> partBySelection = bet.getLegsList().stream()
                .filter(leg -> getSelectionRampId(leg.getPart(0)) != null)
                .collect(toMap(leg -> leg, leg -> getSelectionRampId(leg.getPart(0))));

        return partBySelection.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> groupList.stream()
                        .filter(group -> !group.getSelectionsList().isEmpty())
                        .filter(group -> group.getSelectionsList().stream()
                                .anyMatch(selection -> selection.getSelectionRampId().equals(entry.getValue())))
                        .map(group -> Map.entry(entry.getKey(), group)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String getSelectionRampId(PartOuterClass.Part part) {
        return part.getSelectionInfo().getEntity().getIdsMap().get(RAMP_IDENTIFIER);
    }

    private SgmGroup convertToSgmGroup(FeaturesOuterClass.Group group) {
        SgmGroup.Builder sgmGroupBuilder = SgmGroup.newBuilder();
        SgmGroupQuotingData.Builder sgmGroupQuotingDataBuilder = SgmGroupQuotingData.newBuilder();

        sgmGroupQuotingDataBuilder.setRef(StringValue.of(String.valueOf(group.getGroupId())));

        if (group.hasPriceInfo()) {
            if (group.getPriceInfo().hasDecimal()) {
                sgmGroupQuotingDataBuilder.setPrice(
                        sgmGroupPriceConverter.convertSgmGroupPrice(group.getPriceInfo().getDecimal().getValue())
                );
            } else if (group.getPriceInfo().hasFractional()) {
                sgmGroupQuotingDataBuilder.setPrice(
                        sgmGroupPriceConverter.convertSgmGroupPrice(
                                group.getPriceInfo().getFractional().getNumerator(),
                                group.getPriceInfo().getFractional().getDenominator()
                        )
                );
            }
        }

        sgmGroupBuilder.setSgmGroupQuotingData(sgmGroupQuotingDataBuilder.build());

        return sgmGroupBuilder.build();
    }
}
