package com.flutter.gbp.betstore.writer.convert.streams.fbp.enricher.betcontext;

import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.domain.ImmutableAppliedRewardsContext;
import com.flutter.gbp.betcontext.domain.ImmutableReward;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.rewards.AppliedRewardsContext;
import com.flutter.gbp.fcq.bet.rewards.Reward;
import com.flutter.gbp.fcq.bet.rewards.RewardType;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class AppliedRewardsContextEnricherTest {

    private AppliedRewardsContextEnricher victim;
    private static final String mockRewardId = "reward-id-";
    private int rewardIdCount = 1;
    private static final Map<String, String> mockMetadata = Map.of("key1", "value1", "key2", "value2");

    @DataProvider
    public static Object[][] rewardTypes() {
        return new Object[][]
                {
                        {com.flutter.gbp.betcontext.domain.RewardType.ADAPTIVE, RewardType.ADAPTIVE},
                        {com.flutter.gbp.betcontext.domain.RewardType.UNKNOWN_REWARD_TYPE, RewardType.UNKNOWN_REWARD_TYPE},
                };
    }

    @BeforeMethod
    public void setUp() {
        victim = new AppliedRewardsContextEnricher();
    }

    @DataProvider
    public static Object[][] invalidArgumentValues() {
        return new Object[][]{
                {null, mock(BetContext.class), Bet.newBuilder()},
                {BetOuterClass.Bet.newBuilder().build(), null, Bet.newBuilder()},
                {BetOuterClass.Bet.newBuilder().build(), mock(BetContext.class), null}
        };
    }

    @Test(dataProvider = "invalidArgumentValues")
    public void testNullArguments(BetOuterClass.Bet source, BetContext betContext, Bet.Builder target) {
        victim.enrich(source, betContext, target);
    }

    @DataProvider
    public static Object[][] enrichTestData() {
        return new Object[][]{
                {com.flutter.gbp.betcontext.domain.RewardType.ADAPTIVE, RewardType.ADAPTIVE},
                {com.flutter.gbp.betcontext.domain.RewardType.UNKNOWN_REWARD_TYPE, RewardType.UNKNOWN_REWARD_TYPE},
        };
    }

    @Test(dataProvider = "enrichTestData")
    public void testEnrich(com.flutter.gbp.betcontext.domain.RewardType sourceRewardType,
                           RewardType expectedRewardType) {
        BetOuterClass.Bet source = BetOuterClass.Bet.newBuilder().build();

        BetContext betContext = mock(BetContext.class);

        com.flutter.gbp.betcontext.domain.AppliedRewardsContext appliedRewardsContext =
                makeSourceContext(sourceRewardType);
        when(betContext.appliedReward()).thenReturn(Optional.of(appliedRewardsContext));

        AppliedRewardsContext expectedContext = makeEnrichedContext(expectedRewardType);

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, betContext, target);

        AppliedRewardsContext actualContext = target.getAppliedRewardContext();
        assertEquals(expectedContext, actualContext);
    }

    @Test
    public void testEnrichWithNoAppliedRewards() {
        BetOuterClass.Bet source = BetOuterClass.Bet.newBuilder().build();

        BetContext betContext = mock(BetContext.class);

        when(betContext.appliedReward()).thenReturn(Optional.empty());

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, betContext, target);

        AppliedRewardsContext actualContext = target.getAppliedRewardContext();
        assertNotNull(actualContext);
        assertTrue(actualContext.getRewardsList().isEmpty());
    }

    @Test
    public void testEnrichWithContextWithNoRewards() {
        BetOuterClass.Bet source = BetOuterClass.Bet.newBuilder().build();

        com.flutter.gbp.betcontext.domain.AppliedRewardsContext context =
                mock(com.flutter.gbp.betcontext.domain.AppliedRewardsContext.class);

        when(context.rewards()).thenReturn(Optional.of(List.of()));

        BetContext betContext = mock(BetContext.class);

        when(betContext.appliedReward()).thenReturn(Optional.of(context));

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, betContext, target);

        AppliedRewardsContext actualContext = target.getAppliedRewardContext();
        assertNotNull(actualContext);
        assertTrue(actualContext.getRewardsList().isEmpty());
    }

    @Test
    public void testEnrichWithContextWithNullReward() {
        BetOuterClass.Bet source = BetOuterClass.Bet.newBuilder().build();

        com.flutter.gbp.betcontext.domain.AppliedRewardsContext context =
                mock(com.flutter.gbp.betcontext.domain.AppliedRewardsContext.class);


        List<com.flutter.gbp.betcontext.domain.Reward> rewards = new ArrayList<>();
        rewards.add(null);

        when(context.rewards()).thenReturn(Optional.of(rewards));

        BetContext betContext = mock(BetContext.class);

        when(betContext.appliedReward()).thenReturn(Optional.of(context));

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, betContext, target);

        AppliedRewardsContext actualContext = target.getAppliedRewardContext();
        assertNotNull(actualContext);
        assertNotNull(actualContext.getRewardsList());
        assertTrue(actualContext.getRewardsList().isEmpty());
    }

    @Test
    public void testEnrichWithNullRewardType() {
        BetOuterClass.Bet source = BetOuterClass.Bet.newBuilder().build();

        com.flutter.gbp.betcontext.domain.AppliedRewardsContext context =
                mock(com.flutter.gbp.betcontext.domain.AppliedRewardsContext.class);


        List<com.flutter.gbp.betcontext.domain.Reward> rewards = new ArrayList<>();
        rewards.add(new BadRewardType());

        when(context.rewards()).thenReturn(Optional.of(rewards));

        BetContext betContext = mock(BetContext.class);

        when(betContext.appliedReward()).thenReturn(Optional.of(context));

        Bet.Builder target = Bet.newBuilder();

        victim.enrich(source, betContext, target);

        assertFalse(target.hasAppliedRewardContext());
    }

    private static class BadRewardType implements com.flutter.gbp.betcontext.domain.Reward {

        //relevant field to the enricher
        @Override
        public com.flutter.gbp.betcontext.domain.RewardType type() {
            return null;
        }

        //unused fields to satisfy interface
        @Override
        public String id() {
            return null;
        }

        @Override
        public Map<String, String> metadata() {
            return null;
        }
    }

    private com.flutter.gbp.betcontext.domain.AppliedRewardsContext makeSourceContext(com.flutter.gbp.betcontext.domain.RewardType... rewardTypes) {

        List<com.flutter.gbp.betcontext.domain.Reward> rewardList = new ArrayList<>();

        for (com.flutter.gbp.betcontext.domain.RewardType rewardType : rewardTypes) {
            com.flutter.gbp.betcontext.domain.Reward reward = ImmutableReward.builder()
                    .withId(mockRewardId + rewardIdCount++)
                    .withMetadata(mockMetadata)
                    .withType(rewardType)
                    .build();
            rewardList.add(reward);
        }

        return ImmutableAppliedRewardsContext.builder()
                .withRewards(rewardList)
                .build();
    }

    private static AppliedRewardsContext makeEnrichedContext(RewardType... rewardTypes) {

        List<Reward> rewardList = new ArrayList<>();
        for (RewardType rewardType : rewardTypes) {
            Reward reward = Reward.newBuilder()
                    .setRewardType(rewardType)
                    .build();
            rewardList.add(reward);
        }

        return AppliedRewardsContext.newBuilder()
                .addAllRewards(rewardList)
                .build();

    }
}
