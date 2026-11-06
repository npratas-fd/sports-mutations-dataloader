package com.flutter.gbp.betstore.writer.convert.utils;

import com.google.protobuf.DoubleValue;
import java.util.function.BooleanSupplier;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class SgmGroupPriceConverterTest {

    @Mock
    private BooleanSupplier roundStartPriceHalfUpToggle;

    private SgmGroupPriceConverter victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        victim = new SgmGroupPriceConverter(roundStartPriceHalfUpToggle);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldTestInvalidArguments() {
        new SgmGroupPriceConverter(null);
    }

    @Test
    public void shouldTestSgmGroupPriceConversionWithDouble() {
        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(true);
        DoubleValue halfUpValue = victim.convertSgmGroupPrice(1.2351);
        assertEquals(halfUpValue.getValue(), 1.24);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(true);
        halfUpValue = victim.convertSgmGroupPrice(1.000902);
        assertEquals(halfUpValue.getValue(), 1.001);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(true);
        halfUpValue = victim.convertSgmGroupPrice(1.00009902);
        assertEquals(halfUpValue.getValue(), 1.000);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(false);
        DoubleValue downValue = victim.convertSgmGroupPrice(1.2351);
        assertEquals(downValue.getValue(), 1.23);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(false);
        downValue = victim.convertSgmGroupPrice(1.000902);
        assertEquals(downValue.getValue(), 1.000);
    }

    @Test
    public void shouldTestSgmGroupPriceConversionWithNumeratorAndDenominator() {
        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(true);
        DoubleValue halfUpValue = victim.convertSgmGroupPrice(5, 1000);
        assertEquals(halfUpValue.getValue(), 1.005);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(true);
        halfUpValue = victim.convertSgmGroupPrice(445, 1000);
        assertEquals(halfUpValue.getValue(), 1.45);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(false);
        DoubleValue downValue = victim.convertSgmGroupPrice(5, 1000);
        assertEquals(downValue.getValue(), 1.005);

        when(roundStartPriceHalfUpToggle.getAsBoolean()).thenReturn(false);
        downValue = victim.convertSgmGroupPrice(445, 1000);
        assertEquals(downValue.getValue(), 1.44);
    }

}