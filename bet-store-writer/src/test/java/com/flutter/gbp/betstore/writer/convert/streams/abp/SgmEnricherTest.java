package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.betstore.writer.convert.utils.SgmGroupPriceConverter;
import com.flutter.gbp.fcq.bet.Bet;
import com.flutter.gbp.fcq.bet.leg.Leg;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.retail.proto.bet.BetOuterClass;
import com.ppb.platform.sb.retail.proto.bet.EntityOuterClass;
import com.ppb.platform.sb.retail.proto.bet.FeaturesOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class SgmEnricherTest {

    @Mock
    private SgmGroupPriceConverter sgmGroupPriceConverter;

    private SgmEnricher victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        victim = new SgmEnricher(sgmGroupPriceConverter);
        when(sgmGroupPriceConverter.convertSgmGroupPrice(1.5d)).thenReturn(DoubleValue.of(1.5d));
        when(sgmGroupPriceConverter.convertSgmGroupPrice(3.5d)).thenReturn(DoubleValue.of(3.5d));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldTestInvalidArguments() {
        new SgmEnricher(null);
    }

    @Test
    public void testEnrichWithSgmData_NoFeatures() {
        Bet.Builder betBuilder = Bet.newBuilder();
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder().build();

        victim.enrichWithSgmData(bet, betBuilder);

        // No SGM groups were added
        assertEquals(betBuilder.getSgmGroupsCount(), 0);
    }

    @Test
    public void testEnrichWithSgmData_NoGroups() {
        Bet.Builder betBuilder = Bet.newBuilder();
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setFeatures(FeaturesOuterClass.Features.newBuilder().setSgpp(FeaturesOuterClass.SGPPContext.newBuilder()).build())
                .build();

        victim.enrichWithSgmData(bet, betBuilder);

        // No SGM groups were added
        assertEquals(betBuilder.getSgmGroupsCount(), 0);
    }

    @Test
    public void testEnrichWithSgmData_GroupsPresent() {
        BetOuterClass.Bet bet = getAbpBet();
        Bet.Builder betBuilder = getBetBuilder();

        victim.enrichWithSgmData(bet, betBuilder);

        // Verify that sgmGroups are added to betBuilder
        assertEquals(betBuilder.getSgmGroupsCount(), 2);
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getRef().getValue(), "1");
        assertEquals(betBuilder.getSgmGroups(0).getSgmGroupQuotingData().getPrice(), DoubleValue.of(1.5d));
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getRef().getValue(), "2");
        assertEquals(betBuilder.getSgmGroups(1).getSgmGroupQuotingData().getPrice(), DoubleValue.of(3.5d));

        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(0).getLegQuotingData().getGroupRef(0), StringValue.of("1"));
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRefCount(), 1);
        assertEquals(betBuilder.getLegs(1).getLegQuotingData().getGroupRef(0), StringValue.of("2"));
    }

    private Bet.Builder getBetBuilder() {
        Bet.Builder betBuilder = Bet.newBuilder();

        betBuilder.addLegs(Leg.newBuilder().setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(1).build()).build());
        betBuilder.addLegs(Leg.newBuilder().setLegQuotingData(LegQuotingData.newBuilder().setLegNumber(2).build()).build());
        return betBuilder;
    }

    private BetOuterClass.Bet getAbpBet() {
        FeaturesOuterClass.Group group1 = FeaturesOuterClass.Group.newBuilder()
                .setGroupId(1)
                .addSelections(FeaturesOuterClass.Selection.newBuilder().setSelectionRampId("100").build())
                .setPriceInfo(FeaturesOuterClass.PriceInfo.newBuilder()
                        .setDecimal(DoubleValue.of(1.5d))
                        .build())
                .build();
        FeaturesOuterClass.Group group2 = FeaturesOuterClass.Group.newBuilder()
                .setGroupId(2)
                .addSelections(FeaturesOuterClass.Selection.newBuilder().setSelectionRampId("200").build())
                .setPriceInfo(FeaturesOuterClass.PriceInfo.newBuilder()
                        .setDecimal(DoubleValue.of(3.5d))
                        .build())
                .build();
        FeaturesOuterClass.Features features = FeaturesOuterClass.Features.newBuilder()
                .setSgpp(FeaturesOuterClass.SGPPContext.newBuilder().addGroups(group1).addGroups(group2).build())
                .build();

        PartOuterClass.Part part1 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(EntityOuterClass.Entity.newBuilder()
                                        .putIds("RAMP", "100")
                                        .build())
                                .build())
                        .build();
        PartOuterClass.Part part2 =
                PartOuterClass.Part.newBuilder()
                        .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                                .setEntity(EntityOuterClass.Entity.newBuilder()
                                        .putIds("RAMP", "200")
                                        .build())
                                .build())
                        .build();
        LegOuterClass.Leg leg1 = LegOuterClass.Leg.newBuilder().setLegNumber(1).addPart(part1).build();
        LegOuterClass.Leg leg2 = LegOuterClass.Leg.newBuilder().setLegNumber(2).addPart(part2).build();
        return BetOuterClass.Bet.newBuilder()
                .setFeatures(features)
                .addLegs(leg1)
                .addLegs(leg2)
                .build();
    }

}