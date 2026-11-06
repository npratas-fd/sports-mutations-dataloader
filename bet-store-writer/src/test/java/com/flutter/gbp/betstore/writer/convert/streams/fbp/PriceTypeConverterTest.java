package com.flutter.gbp.betstore.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.PriceType;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class PriceTypeConverterTest {

    private PriceTypeConverter victim;
    @BeforeMethod
    public void setUp() {
        victim = new PriceTypeConverter();
    }

    @Test(dataProvider = "priceTypeConversionDataProvider")
    public void shouldTestPriceTypeConversion(FractionalPriceOuterClass.FractionalPrice.PriceType source, PriceType target) {
        assertEquals(victim.convert(source), target);
    }

    @DataProvider
    private Object[][] priceTypeConversionDataProvider() {
        return new Object[][]{
                {null, PriceType.UNKNOWN_PRICE_TYPE},
                {FractionalPriceOuterClass.FractionalPrice.PriceType.UNRECOGNIZED, PriceType.UNKNOWN_PRICE_TYPE},
                {FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE, PriceType.LIVE},
                {FractionalPriceOuterClass.FractionalPrice.PriceType.GUARANTEED, PriceType.GUARANTEED},
                {FractionalPriceOuterClass.FractionalPrice.PriceType.STARTING, PriceType.STARTING},
                {FractionalPriceOuterClass.FractionalPrice.PriceType.DIVIDEND, PriceType.DIVIDEND}
        };
    }
}