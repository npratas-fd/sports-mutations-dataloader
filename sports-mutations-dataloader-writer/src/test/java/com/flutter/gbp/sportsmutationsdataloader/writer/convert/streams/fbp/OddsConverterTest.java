package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.odds.DecimalOdds;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class OddsConverterTest {
    private OddsConverter victim;

    @BeforeMethod
    public void setup() {
        victim = new OddsConverter();
    }

    @Test(dataProvider = "fractionConversionDataProvider")
    public void shouldTestFractionConversion(FractionOuterClass.Fraction source, FractionalOdds expectedFractionalOdds) {
        Odds odds = victim.convert(source);
        validateOddsConversion(odds, null, expectedFractionalOdds);
    }

    @Test(dataProvider = "combinedPriceConversionDataProvider")
    public void shouldTestCombinedPriceConversion(CombinedPriceOuterClass.CombinedPrice source, DecimalOdds expectedDecimalOdds,
                                                  FractionalOdds expectedFractionalOdds) {
        Odds odds = victim.convert(source);
        validateOddsConversion(odds, expectedDecimalOdds, expectedFractionalOdds);
    }

    private void validateOddsConversion(Odds odds, DecimalOdds expectedDecimalOdds, FractionalOdds expectedFractionalOdds) {
        assertNotNull(odds);

        if (expectedDecimalOdds != null) {
            assertEquals(odds.getTrueOdds().getDecimalOdds().getDecimalOdds(), expectedDecimalOdds.getDecimalOdds());
        }

        if (expectedFractionalOdds != null) {
            assertEquals(odds.getTrueOdds().getFractionalOdds().getNumerator(), expectedFractionalOdds.getNumerator());
            assertEquals(odds.getTrueOdds().getFractionalOdds().getDenominator(), expectedFractionalOdds.getDenominator());
        }
    }

    @DataProvider
    private Object[][] fractionConversionDataProvider() {
        return new Object[][]{
                {getFraction(0, 2), getFractionalOdds(0, 2)},
                {getFraction(4, 4), getFractionalOdds(4, 4)},
                {getFraction(3, 0), null}
        };
    }

    @DataProvider
    private Object[][] combinedPriceConversionDataProvider() {
        return new Object[][]{
                {getCombinedPrice(0, 2), getDecimalOdds(0.0), getFractionalOdds(0, 2)},
                {getCombinedPrice(4, 4), getDecimalOdds(1.0), getFractionalOdds(4, 4)},
                {getCombinedPrice(3, 0), getDecimalOdds(Double.POSITIVE_INFINITY), null}
        };
    }

    private DecimalOdds getDecimalOdds(double value) {
        return DecimalOdds.newBuilder()
                .setDecimalOdds(DoubleValue.of(value))
                .build();
    }

    private FractionalOdds getFractionalOdds(int numerator, int denominator) {
        return FractionalOdds.newBuilder()
                .setNumerator(Int32Value.of(numerator))
                .setDenominator(Int32Value.of(denominator))
                .build();
    }

    private FractionOuterClass.Fraction getFraction(int numerator, int denominator) {
        return FractionOuterClass.Fraction.newBuilder()
                .setNumerator(numerator)
                .setDenominator(denominator)
                .build();
    }

    private CombinedPriceOuterClass.CombinedPrice getCombinedPrice(int numerator, int denominator) {
        return CombinedPriceOuterClass.CombinedPrice.newBuilder()
                .setFractional(FractionOuterClass.Fraction.newBuilder()
                        .setNumerator(numerator)
                        .setDenominator(denominator)
                        .build())
                .setDecimal((double) numerator / denominator)
                .build();
    }
}