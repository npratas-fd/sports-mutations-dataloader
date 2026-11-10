package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams;

import com.flutter.gbp.fcq.bet.BetClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class BetClassConverterTest<T extends Enum<T>> {

    private BetClassConverter<T> victim;

    @BeforeMethod
    public void setUp() {
        victim = new BetClassConverter<>();
    }
    @Test(dataProvider = "betTypeConversionDataProvider")
    public void shouldTestLegStatusConversion(T source, BetClass target) {
        assertEquals(victim.convert(source), target);
    }

    @DataProvider
    private Object[][] betTypeConversionDataProvider() {
        return new Object[][]{
                {null, BetClass.UNKNOWN_BET_CLASS},
                //FBP
                {com.ppb.platform.sb.fbp.proto.bet.LegBetTypeOuterClass.LegBetType.UNRECOGNIZED, BetClass.UNKNOWN_BET_CLASS},
                {com.ppb.platform.sb.fbp.proto.bet.LegBetTypeOuterClass.LegBetType.UNKNOWN_LEG_BET_TYPE, BetClass.UNKNOWN_BET_CLASS},
                {com.ppb.platform.sb.fbp.proto.bet.LegBetTypeOuterClass.LegBetType.WIN, BetClass.WIN},
                {com.ppb.platform.sb.fbp.proto.bet.LegBetTypeOuterClass.LegBetType.EACHWAY, BetClass.EACHWAY},
                //ABP
                {com.ppb.platform.sb.retail.proto.bet.LegBetTypeOuterClass.LegBetType.UNRECOGNIZED, BetClass.UNKNOWN_BET_CLASS},
                {com.ppb.platform.sb.retail.proto.bet.LegBetTypeOuterClass.LegBetType.UNKNOWN_LEG_BET_TYPE, BetClass.UNKNOWN_BET_CLASS},
                {com.ppb.platform.sb.retail.proto.bet.LegBetTypeOuterClass.LegBetType.WIN, BetClass.WIN},
                {com.ppb.platform.sb.retail.proto.bet.LegBetTypeOuterClass.LegBetType.EACHWAY, BetClass.EACHWAY}
        };
    }
}