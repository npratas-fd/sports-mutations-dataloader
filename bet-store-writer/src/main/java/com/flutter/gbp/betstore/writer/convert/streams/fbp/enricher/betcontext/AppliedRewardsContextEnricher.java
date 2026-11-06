package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.rewards.AppliedRewardsContext;
import com.flutter.gbp.fcq.bet.rewards.Reward;
import com.flutter.gbp.fcq.bet.rewards.RewardType;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class AppliedRewardsContextEnricher implements BetContextEnricher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AppliedRewardsContextEnricher.class.getSimpleName());

    @Override
    public void enrich(BetOuterClass.Bet source, BetContext betContext, Bet.Builder target) {
        List<Reward> rewards = ofNullable(betContext)
                .flatMap(BetContext::appliedReward)
                .flatMap(com.flutter.gbp.betcontext.domain.AppliedRewardsContext::rewards)
                .map(this::convertRewardList)
                .orElseGet(List::of);

        if (rewards.isEmpty()) {
            return;
        }

        AppliedRewardsContext appliedRewardsContext = AppliedRewardsContext.newBuilder()
                .addAllRewards(rewards)
                .build();

        ofNullable(target)
                .map(bet -> bet.setAppliedRewardContext(appliedRewardsContext));
    }

    private List<Reward> convertRewardList(List<com.flutter.gbp.betcontext.domain.Reward> rewards) {
        return rewards.stream()
                .filter(Objects::nonNull)
                .filter(reward -> reward.type() != null)
                .map(this::convertToReward)
                .collect(toList());
    }

    private Reward convertToReward(com.flutter.gbp.betcontext.domain.Reward sourceReward) {
        RewardType rewardType = mapRewardType(sourceReward);
        Reward result = Reward.newBuilder()
                .setRewardType(rewardType)
                .build();

        LOGGER.debug("operation=convertToReward, sourceReward={}, result={}, msg='Converted reward from " +
                "bet context domain to internal domain'", sourceReward, result);

        return result;
    }

    private RewardType mapRewardType(com.flutter.gbp.betcontext.domain.Reward sourceReward) {
        try {
            return RewardType.valueOf(sourceReward.type().name());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("operation=mapRewardType, sourceReward={}, msg='Failed to map reward type'", sourceReward);
            return RewardType.UNKNOWN_REWARD_TYPE;
        }
    }
}
