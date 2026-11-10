package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.LegStatusConverter;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.PriceTypeConverter;
import com.flutter.gbp.fcq.bet.Price;
import com.flutter.gbp.fcq.bet.PriceType;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.retail.proto.bet.EntityOuterClass;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.EachwayInfoOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.retail.proto.bet.part.PartOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class LegQuotingDataConverterTest {
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";

    @Mock
    private LegStatusConverter<PartOuterClass.Part.Result> legStatusConverter;

    @Mock
    private PriceTypeConverter priceTypeConverter;

    @Mock
    private StartPriceConverter startPriceConverter;

    @InjectMocks
    private LegQuotingDataConverterImpl victim;

    private AutoCloseable openMocks;
    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test(dataProvider = "testConvertDataProvider")
    public void testConvert(LegOuterClass.Leg leg, Boolean expectedHasIdsFields) {
        // Mock behavior of dependencies
        when(legStatusConverter.convert(PartOuterClass.Part.Result.WON)).thenReturn(LegStatus.WON);
        when(priceTypeConverter.convert(FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE)).thenReturn(PriceType.LIVE);
        when(startPriceConverter.convertStartPrice(any())).thenReturn(1.5d);

        // Perform conversion
        LegQuotingData result = victim.convert(leg, leg.getPart(0));

        // Assertions
        assertNotNull(result);
        if (expectedHasIdsFields) {
            assertEquals(result.getSelectionId(), 11122L);
        } else {
            assertEquals(result.getSelectionId(), 0);
        }
        assertEquals(result.getLegNumber(), 1);
        assertEquals(result.getLegStatus(), LegStatus.WON);
        assertEquals(result.hasMarketId(), expectedHasIdsFields);
        if (expectedHasIdsFields) {
            assertEquals(result.getMarketId(), StringValue.of("924.11"));
        } else {
            assertEquals(result.getMarketId(), StringValue.getDefaultInstance());
        }
        assertEquals(result.hasMarketType(), expectedHasIdsFields);
        if (expectedHasIdsFields) {
            assertEquals(result.getMarketType(), StringValue.of("MATCH_ODDS"));
        } else {
            assertEquals(result.getMarketType(), StringValue.getDefaultInstance());
        }
        assertEquals(result.getPrice(), Price.newBuilder().setNumerator(2).setDenominator(3).build());
        assertEquals(result.getOriginalPrice(), Price.newBuilder().setNumerator(2).setDenominator(3).build());
        assertEquals(result.getStartPrice(), 1.5d);
        assertEquals(result.getOriginalBasePrice(), Price.getDefaultInstance());
        assertEquals(result.getPriceType(), PriceType.LIVE);
        assertEquals(result.getRule4Deductions(), 0.1d);
        assertEquals(result.getDeadHeatWinDeductions(), 0.1d);
        assertEquals(result.getDeadHeatEachwayDeductions(), 0.1d);
        assertEquals(result.getCombinationNumber(), StringValue.of("5"));
        assertEquals(result.getEachwayDecimalFactor(), 0.75d);
        assertEquals(result.getEachwayOffset(), 0d);
    }

    @Test
    public void testConvertWithNullPart() {
        LegOuterClass.Leg leg = LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .build();

        LegQuotingData result = victim.convert(leg, null);

        assertNotNull(result);
        assertEquals(result.getSelectionId(), 0L);
        assertEquals(result.getLegNumber(), 1);
        assertEquals(result.getLegStatus(), LegStatus.UNKNOWN_LEG_STATUS);
        assertEquals(result.getMarketId(), StringValue.of(""));
        assertEquals(result.getMarketType(), StringValue.of(""));
        assertEquals(result.getPrice(), Price.getDefaultInstance());
        assertEquals(result.getOriginalPrice(), Price.getDefaultInstance());
        assertEquals(result.getStartPrice(), 0d);
        assertEquals(result.getOriginalBasePrice(), Price.getDefaultInstance());
        assertEquals(result.getPriceType(), PriceType.UNKNOWN_PRICE_TYPE);
        assertEquals(result.getRule4Deductions(), 0d);
        assertEquals(result.getDeadHeatWinDeductions(), 0d);
        assertEquals(result.getDeadHeatEachwayDeductions(), 0d);
        assertEquals(result.getCombinationNumber(), StringValue.of(""));
        assertEquals(result.getEachwayDecimalFactor(), 0d);
        assertEquals(result.getEachwayOffset(), 0);
    }

    @Test
    public void testConvertWithInvalidSelectionId() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setResult(PartOuterClass.Part.Result.WON)
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "invalid_id")
                                .build())
                        .build())
                .build();

        LegOuterClass.Leg leg = LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .addPart(part)
                .build();

        // Mock behavior of dependencies
        when(legStatusConverter.convert(PartOuterClass.Part.Result.WON)).thenReturn(LegStatus.WON);
        when(priceTypeConverter.convert(FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE)).thenReturn(PriceType.LIVE);

        // Perform conversion
        LegQuotingData result = victim.convert(leg, leg.getPart(0));

        assertNotNull(result);
        assertEquals(result.getSelectionId(), 0L);
    }

    @DataProvider
    public Object[][] testConvertDataProvider() {
        return new Object[][]{
                {getLeg(true), true},
                {getLeg(false), false}
        };
    }

    @Nonnull
    private static LegOuterClass.Leg getLeg(boolean withIdsFields) {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .putIds(withIdsFields ? SPORTEX_IDENTIFIER : "UNUSED", "11122")
                                .build())
                        .build())
                .setResult(PartOuterClass.Part.Result.WON)
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .putIds(withIdsFields ? SPORTSBOOK_IDENTIFIER : "UNUSED", "924.11")
                                .build())
                        .build())
                .setMarketTypeInfo(PartOuterClass.MarketTypeInfo.newBuilder()
                        .setEntity(EntityOuterClass.Entity.newBuilder()
                                .putIds(withIdsFields ? SPORTSBOOK_IDENTIFIER : "UNUSED", "MATCH_ODDS")
                                .build())
                        .build())
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setTaken(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(2)
                                .setDenominator(3)
                                .build())
                        .setStarting(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(1)
                                .setDenominator(2)
                                .build())
                        .setType(FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE)
                        .build())
                .setRule4Deductions(0.1d)
                .setDeadHeatWinDeductions(0.1d)
                .setEachwayInfo(EachwayInfoOuterClass.EachwayInfo.newBuilder()
                        .setDeadHeatEachwayDeductions(DoubleValue.of(0.1d))
                        .setEachwayFactor(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(3)
                                .setDenominator(4)
                                .build())
                        .build())
                .setNoCombi(Int32Value.of(5))
                .build();

        return LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .addPart(part)
                .build();
    }
}