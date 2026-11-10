package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp;

import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class FractionalOddsConverterTest {

    private FractionalOddsConverter victim;

    @BeforeMethod
    public void setUp() {
        victim = new FractionalOddsConverter();
    }

    @Test(dataProvider = "fractionalOddsTestData")
    public void testConvert(FractionOuterClass.Fraction source, FractionalOdds expected) {
        FractionalOdds result = victim.convert(source);
        assertEquals(result, expected);
    }

    @DataProvider(name = "fractionalOddsTestData")
    public Object[][] fractionalOddsTestData() {
        return new Object[][]{
                {FractionOuterClass.Fraction.newBuilder().setNumerator(5).setDenominator(2).build(),
                        FractionalOdds.newBuilder().setNumerator(Int32Value.of(5)).setDenominator(Int32Value.of(2)).build()},
                {FractionOuterClass.Fraction.newBuilder().setNumerator(3).setDenominator(4).build(),
                        FractionalOdds.newBuilder().setNumerator(Int32Value.of(3)).setDenominator(Int32Value.of(4)).build()},
                {FractionOuterClass.Fraction.newBuilder().setNumerator(0).setDenominator(1).build(),
                        FractionalOdds.newBuilder().setNumerator(Int32Value.of(0)).setDenominator(Int32Value.of(1)).build()},
                {FractionOuterClass.Fraction.newBuilder().setNumerator(9).setDenominator(3).build(),
                        FractionalOdds.newBuilder().setNumerator(Int32Value.of(9)).setDenominator(Int32Value.of(3)).build()}
        };
    }

}