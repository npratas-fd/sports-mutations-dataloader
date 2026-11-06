package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.fcq.bet.odds.DecimalOdds;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class OddsConverterTest {
    private OddsConverter victim;

    @DataProvider
    private Object[][] fractionConversionDataProvider() {
        return new Object[][]{
                {getFraction(0, 2), getFractionalOdds(0, 2)},
                {getFraction(4, 4), getFractionalOdds(4, 4)},
                {getFraction(3, 0), null}
        };
    }

    @DataProvider
    private Object[][] doubleConversionDataProvider() {
        return new Object[][]{
                {0.0, getDecimalOdds(0.0)},
                {1.0, getDecimalOdds(1.0)},
                {2.5, getDecimalOdds(2.5)},
                {-1.0, getDecimalOdds(-1.0)},
                {-2.5, getDecimalOdds(-2.5)}
        };
    }

    @BeforeMethod
    public void setup() {
        victim = new OddsConverter();
    }

    @Test(dataProvider = "fractionConversionDataProvider")
    public void shouldTestFractionConversion(FractionOuterClass.Fraction source, FractionalOdds expectedFractionalOdds) {
        Odds odds = victim.convert(source);
        validateOddsConversion(odds, null, expectedFractionalOdds);
    }

    @Test(dataProvider = "doubleConversionDataProvider")
    public void shouldTestDoubleConversion(double source, DecimalOdds expectedDecimalOdds) {
        Odds odds = victim.convert(source);
        validateOddsConversion(odds, expectedDecimalOdds, null);
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
}