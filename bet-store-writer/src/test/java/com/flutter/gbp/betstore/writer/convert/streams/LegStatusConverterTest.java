package com.flutter.gbp.betstore.writer.convert.streams;

import com.flutter.gbp.fcq.bet.leg.LegStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class LegStatusConverterTest<T extends Enum<T>> {

    private LegStatusConverter<T> victim;
    @BeforeMethod
    public void setUp() {
        victim = new LegStatusConverter<>();
    }

    @Test(dataProvider = "legStatusConversionDataProvider")
    public void shouldTestLegStatusConversion(T source, LegStatus target) {
        assertEquals(victim.convert(source), target);
    }

    @DataProvider
    private Object[][] legStatusConversionDataProvider() {
        return new Object[][]{
                {null, LegStatus.UNKNOWN_LEG_STATUS},
                {com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part.Result.NONE, LegStatus.UNKNOWN_LEG_STATUS},
                {com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass.Part.Result.NONE, LegStatus.UNKNOWN_LEG_STATUS},
                {com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part.Result.UNRECOGNIZED, LegStatus.UNKNOWN_LEG_STATUS},
                {com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass.Part.Result.UNRECOGNIZED, LegStatus.UNKNOWN_LEG_STATUS},
                {com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part.Result.WON, LegStatus.WON},
                {com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass.Part.Result.WON, LegStatus.WON},
                {com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part.Result.PLACED, LegStatus.PLACED},
                {com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass.Part.Result.PLACED, LegStatus.PLACED},
                {com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part.Result.VOIDED, LegStatus.VOID},
                {com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass.Part.Result.VOIDED, LegStatus.VOID},
                {com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass.Part.Result.LOST, LegStatus.LOST},
                {com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass.Part.Result.LOST, LegStatus.LOST}
        };
    }
}