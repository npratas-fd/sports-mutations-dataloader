package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.fbp.PriceTypeConverter;
import com.flutter.gbp.fcq.bet.Price;
import com.flutter.gbp.fcq.bet.PriceType;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class FbpFractionalPriceWriterTest {

    @Mock
    private StartPriceConverter startPriceConverter;
    @Mock
    private PriceTypeConverter priceTypeConverter;

    private AutoCloseable openMocks;
    private FbpFractionalPriceWriter victim;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        victim = new FbpFractionalPriceWriter(startPriceConverter, priceTypeConverter);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void testWritePopulatesEveryPriceFieldTheFractionalPriceCarries() {
        FractionalPriceOuterClass.FractionalPrice fractionalPrice = FractionalPriceOuterClass.FractionalPrice.newBuilder()
                .setCurrent(fraction(4, 5))
                .setMarginated(fraction(3, 5))
                .setStarting(fraction(2, 5))
                .setRaw(fraction(1, 5))
                .setType(FractionalPriceOuterClass.FractionalPrice.PriceType.STARTING)
                .build();
        LegQuotingData.Builder builder = LegQuotingData.newBuilder();

        when(startPriceConverter.convertStartPrice(fraction(2, 5))).thenReturn(2.5d);
        when(priceTypeConverter.convert(FractionalPriceOuterClass.FractionalPrice.PriceType.STARTING)).thenReturn(PriceType.STARTING);

        victim.write(fractionalPrice, builder);

        LegQuotingData result = builder.build();
        assertEquals(result.getPrice(), price(4, 5));
        assertEquals(result.getOriginalPrice(), price(3, 5));
        assertEquals(result.getStartPrice(), 2.5d);
        assertEquals(result.getOriginalBasePrice(), price(1, 5));
        assertEquals(result.getPriceType(), PriceType.STARTING);
    }

    @Test
    public void testWriteLeavesTheFieldsTheFractionalPriceDoesNotCarryUntouched() {
        // A partially populated upstream price must not overwrite the proto defaults with zeroed prices
        FractionalPriceOuterClass.FractionalPrice fractionalPrice = FractionalPriceOuterClass.FractionalPrice.newBuilder()
                .setCurrent(fraction(4, 5))
                .setType(FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE)
                .build();
        LegQuotingData.Builder builder = LegQuotingData.newBuilder();

        when(priceTypeConverter.convert(FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE)).thenReturn(PriceType.LIVE);

        victim.write(fractionalPrice, builder);

        LegQuotingData result = builder.build();
        assertEquals(result.getPrice(), price(4, 5));
        assertEquals(result.getOriginalPrice(), Price.getDefaultInstance());
        assertEquals(result.getStartPrice(), 0d);
        assertEquals(result.getOriginalBasePrice(), Price.getDefaultInstance());
        assertEquals(result.getPriceType(), PriceType.LIVE);
        verifyNoInteractions(startPriceConverter);
    }

    @Test
    public void testWriteAlwaysWritesThePriceTypeEvenWhenTheFractionalPriceCarriesNoFraction() {
        LegQuotingData.Builder builder = LegQuotingData.newBuilder();

        when(priceTypeConverter.convert(any())).thenReturn(PriceType.UNKNOWN_PRICE_TYPE);

        victim.write(FractionalPriceOuterClass.FractionalPrice.getDefaultInstance(), builder);

        LegQuotingData result = builder.build();
        assertEquals(result.getPrice(), Price.getDefaultInstance());
        assertEquals(result.getPriceType(), PriceType.UNKNOWN_PRICE_TYPE);
    }

    @Test
    public void testWriteWritesNothingWhenNoPriceWasResolved() {
        LegQuotingData.Builder builder = LegQuotingData.newBuilder();

        victim.write(null, builder);

        assertEquals(builder.build(), LegQuotingData.getDefaultInstance());
        verifyNoInteractions(startPriceConverter);
        verifyNoInteractions(priceTypeConverter);
    }

    @Test
    public void testConstructorRejectsMissingConverters() {
        assertThrows(IllegalArgumentException.class, () -> new FbpFractionalPriceWriter(null, priceTypeConverter));
        assertThrows(IllegalArgumentException.class, () -> new FbpFractionalPriceWriter(startPriceConverter, null));
    }

    private static FractionOuterClass.Fraction fraction(int numerator, int denominator) {
        return FractionOuterClass.Fraction.newBuilder().setNumerator(numerator).setDenominator(denominator).build();
    }

    private static Price price(int numerator, int denominator) {
        return Price.newBuilder().setNumerator(numerator).setDenominator(denominator).build();
    }
}
