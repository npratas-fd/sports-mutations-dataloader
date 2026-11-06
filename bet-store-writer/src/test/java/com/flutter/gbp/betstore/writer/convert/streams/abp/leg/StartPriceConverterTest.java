package com.flutter.gbp.betstore.writer.convert.streams.abp.leg;

import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import java.util.function.BooleanSupplier;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

public class StartPriceConverterTest {

    @Mock
    private BooleanSupplier featureToggle;

    private StartPriceConverter victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = openMocks(this);
        victim = new StartPriceConverter(featureToggle);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(dataProvider = "startPriceConversionDataProvider")
    public void shouldTestConversion(FractionOuterClass.Fraction fraction, boolean toggleValue, Double expectedResult) {
        when(featureToggle.getAsBoolean()).thenReturn(toggleValue);
        Double result = victim.convertStartPrice(fraction);
        assertEquals(result, expectedResult);
    }

    @DataProvider
    public static Object[][] startPriceConversionDataProvider() {
        return new Object[][]{
                {getFraction(5, 1000), false, 1.005},
                {getFraction(5, 1000), true, 1.005},
                {getFraction(445, 1000), false, 1.44},
                {getFraction(445, 1000), true, 1.45},
        };
    }

    private static FractionOuterClass.Fraction getFraction(int numerator, int denominator) {
        return FractionOuterClass.Fraction.newBuilder()
                .setNumerator(numerator)
                .setDenominator(denominator)
                .build();
    }
}