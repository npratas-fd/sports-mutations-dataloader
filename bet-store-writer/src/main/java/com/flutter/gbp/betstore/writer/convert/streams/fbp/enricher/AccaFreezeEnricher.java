package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher;

import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.ppb.platform.sb.fbp.proto.bet.changeinterms.ChangeInTermsOuterClass;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AccaFreezeEnricher {

    public void enrich(Bet.Builder bet, List<ChangeInTermsOuterClass.ChangeInTerms> changeInTermsList) {
        if (bet == null || bet.getLegsList() == null || changeInTermsList == null || changeInTermsList.isEmpty()) {
            return;
        }

        Set<Integer> legsWithAccaFreeze = extractLegsWithAccaFreeze(changeInTermsList);

        if (legsWithAccaFreeze.isEmpty()) {
            return;
        }

        updateLegsWithAccaFreeze(bet, legsWithAccaFreeze);
    }

    private Set<Integer> extractLegsWithAccaFreeze(List<ChangeInTermsOuterClass.ChangeInTerms> changeInTermsList) {
        return changeInTermsList.stream()
                .filter(changeInTerms -> changeInTerms.getReasonCode() == ChangeInTermsOuterClass.ChangeInTerms.Reason.LEG_FREEZE)
                .flatMap(changeInTerms -> changeInTerms.getChangeLegsList().stream())
                .map(ChangeInTermsOuterClass.ChangeLeg::getLegNo)
                .collect(Collectors.toSet());
    }

    private void updateLegsWithAccaFreeze(Bet.Builder bet, Set<Integer> legsWithAccaFreeze) {
        for (int i = 0; i < bet.getLegsCount(); i++) {
            Leg leg = bet.getLegs(i);
            if (legsWithAccaFreeze.contains(leg.getLegQuotingData().getLegNumber())) {
                LegQuotingData updatedLegQuotingData = LegQuotingData.newBuilder(leg.getLegQuotingData())
                        .setIsAccaFrozen(true).build();
                Leg updatedLeg = Leg.newBuilder(leg).setLegQuotingData(updatedLegQuotingData).build();
                bet.setLegs(i, updatedLeg);
            }
        }
    }
}
