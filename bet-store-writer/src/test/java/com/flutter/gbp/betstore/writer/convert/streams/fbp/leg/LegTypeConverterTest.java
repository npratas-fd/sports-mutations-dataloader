package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.fcq.bet.leg.LegType;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class LegTypeConverterTest {


    private LegTypeConverter victim;
    @BeforeMethod
    public void setUp() {
        victim = new LegTypeConverter();
    }

    @Test(dataProvider = "legTypeConversionDataProvider")
    public void shouldTestLegTypeConversion(LegOuterClass.Leg.LegType source, LegType target) {
        assertEquals(victim.convert(source), target);
    }

    @DataProvider
    private Object[][] legTypeConversionDataProvider() {
        return new Object[][]{
                {null, LegType.UNKNOWN_LEG_TYPE},
                {LegOuterClass.Leg.LegType.FIRST_GOALSCORER_WINCAST, LegType.UNKNOWN_LEG_TYPE},
                {LegOuterClass.Leg.LegType.LAST_GOALSCORER_WINCAST, LegType.UNKNOWN_LEG_TYPE},
                {LegOuterClass.Leg.LegType.ANYTIME_GOALSCORER_WINCAST, LegType.UNKNOWN_LEG_TYPE},
                {LegOuterClass.Leg.LegType.UNRECOGNIZED, LegType.UNKNOWN_LEG_TYPE},
                {LegOuterClass.Leg.LegType.SIMPLE_SELECTION, LegType.SS},
                {LegOuterClass.Leg.LegType.FORECAST, LegType.SF},
                {LegOuterClass.Leg.LegType.REVERSE_FORECAST, LegType.RF},
                {LegOuterClass.Leg.LegType.COMBINATION_FORECAST, LegType.CF},
                {LegOuterClass.Leg.LegType.TRICAST, LegType.TC},
                {LegOuterClass.Leg.LegType.COMBINATION_TRICAST, LegType.CT},
                {LegOuterClass.Leg.LegType.SCORECAST, LegType.SC},
                {LegOuterClass.Leg.LegType.MOVING_HANDICAP, LegType.MH},
                {LegOuterClass.Leg.LegType.WESTERN_HANDICAP, LegType.WH},
                {LegOuterClass.Leg.LegType.HIGHER_AND_LOWER, LegType.HL},
                {LegOuterClass.Leg.LegType.HIGHER_AND_LOWER_SPLIT_LINE, LegType.SL},
                {LegOuterClass.Leg.LegType.OUTCOME_BASED, LegType.OB}
        };
    }
}