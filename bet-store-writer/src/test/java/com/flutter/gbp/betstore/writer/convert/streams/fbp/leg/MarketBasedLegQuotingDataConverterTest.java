package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.streams.LegStatusConverter;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.flutter.gbp.fcq.bet.leg.Part;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.EachwayInfoOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

public class MarketBasedLegQuotingDataConverterTest {

    private static final String BET_ID = "O/123456789/0001";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";

    @Mock
    private LegStatusConverter<PartOuterClass.Part.Result> legStatusConverter;

    @Mock
    private FbpFractionalPriceWriter fractionalPriceWriter;

    @Mock
    private PartConverter partConverter;

    @InjectMocks
    private MarketBasedLegQuotingDataConverterImpl victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void testConvertMapsNonPartFieldsAndAppendsConvertedPart() {
        // The leg built by getLeg() carries no fractionalPrice, so the price falls back to the part's fractionalPrice.
        LegOuterClass.Leg leg = getLeg();
        Part stubbedPart = Part.newBuilder()
                .setMarketId("stub-market")
                .setSelectionId("stub-selection")
                .build();

        when(legStatusConverter.convert(PartOuterClass.Part.Result.WON)).thenReturn(LegStatus.WON);
        when(partConverter.convert(leg.getPart(0))).thenReturn(stubbedPart);

        LegQuotingData result = victim.convert(leg, leg.getPart(0), BET_ID);

        assertNotNull(result);

        // Legacy CBD proto marketId / selectionId are never written by this converter.
        assertEquals(result.getSelectionId(), 0L);
        assertFalse(result.hasMarketId());
        assertEquals(result.getMarketId(), StringValue.getDefaultInstance());

        // The converted Part returned by the (mocked) PartConverter is appended verbatim.
        assertEquals(result.getPartsList().size(), 1);
        assertEquals(result.getParts(0), stubbedPart);

        // Non-Part fields on the builder are mapped correctly.
        assertEquals(result.getLegNumber(), 1);
        assertEquals(result.getLegStatus(), LegStatus.WON);
        // marketType is Part state now and is no longer written at LegQuotingData level.
        assertFalse(result.hasMarketType());
        // The price fields themselves are written by the FbpFractionalPriceWriter, from the part's own price here.
        verify(fractionalPriceWriter).write(eq(leg.getPart(0).getFractionalPrice()), any(LegQuotingData.Builder.class));
        assertEquals(result.getRule4Deductions(), 0.1d);
        assertEquals(result.getDeadHeatWinDeductions(), 0.1d);
        assertEquals(result.getDeadHeatEachwayDeductions(), 0.1d);
        assertEquals(result.getCombinationNumber(), StringValue.of("5"));
        assertEquals(result.getEachwayDecimalFactor(), 0.75d);
        assertEquals(result.getEachwayOffset(), 2);
    }

    @Test
    public void testConvertWritesTheLegFractionalPriceWhenPresentInsteadOfThePartFractionalPrice() {
        FractionalPriceOuterClass.FractionalPrice legFractionalPrice = FractionalPriceOuterClass.FractionalPrice.newBuilder()
                .setCurrent(FractionOuterClass.Fraction.newBuilder().setNumerator(4).setDenominator(5).build())
                .setMarginated(FractionOuterClass.Fraction.newBuilder().setNumerator(3).setDenominator(5).build())
                .setStarting(FractionOuterClass.Fraction.newBuilder().setNumerator(2).setDenominator(5).build())
                .setRaw(FractionOuterClass.Fraction.newBuilder().setNumerator(1).setDenominator(5).build())
                .setType(FractionalPriceOuterClass.FractionalPrice.PriceType.STARTING)
                .build();
        LegOuterClass.Leg leg = getLeg().toBuilder()
                .setFractionalPrice(legFractionalPrice)
                .build();

        when(legStatusConverter.convert(PartOuterClass.Part.Result.WON)).thenReturn(LegStatus.WON);

        LegQuotingData result = victim.convert(leg, leg.getPart(0), BET_ID);

        assertNotNull(result);
        // Leg's combined price takes precedence over the part's own price.
        verify(fractionalPriceWriter).write(eq(legFractionalPrice), any(LegQuotingData.Builder.class));
        verify(fractionalPriceWriter, never())
                .write(eq(leg.getPart(0).getFractionalPrice()), any(LegQuotingData.Builder.class));
    }

    @Test
    public void testConvertWritesNoPriceWhenNeitherLegNorPartHaveFractionalPrice() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setResult(PartOuterClass.Part.Result.WON)
                .build();
        LegOuterClass.Leg leg = LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .addPart(part)
                .build();

        when(legStatusConverter.convert(PartOuterClass.Part.Result.WON)).thenReturn(LegStatus.WON);

        LegQuotingData result = victim.convert(leg, part, BET_ID);

        assertNotNull(result);
        verify(fractionalPriceWriter).write(isNull(), any(LegQuotingData.Builder.class));
    }

    @Test
    public void testConvertDoesNotAppendPart_whenPartConverterReturnsNull() {
        LegOuterClass.Leg leg = getLeg();

        when(legStatusConverter.convert(PartOuterClass.Part.Result.WON)).thenReturn(LegStatus.WON);
        when(partConverter.convert(leg.getPart(0))).thenReturn(null);

        LegQuotingData result = victim.convert(leg, leg.getPart(0), BET_ID);

        assertNotNull(result);
        assertEquals(result.getPartsList().size(), 0);
        // Non-Part mapping still runs — smoke-check one field.
        assertEquals(result.getLegNumber(), 1);
    }

    @Test
    public void testConvertOnlyAppendsTheGivenPartOfAMultiplePartLeg() {
        PartOuterClass.Part firstFbpPart = buildSelectionPart(SPORTEX_IDENTIFIER, "11122");
        PartOuterClass.Part secondFbpPart = buildSelectionPart(SPORTEX_IDENTIFIER, "33344");
        LegOuterClass.Leg leg = LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .setLegType(LegOuterClass.Leg.LegType.SIMPLE_SELECTION)
                .addPart(firstFbpPart)
                .addPart(secondFbpPart)
                .build();

        when(legStatusConverter.convert(any())).thenReturn(LegStatus.UNKNOWN_LEG_STATUS);
        Part secondPart = Part.newBuilder().setSelectionId("33344").build();
        when(partConverter.convert(secondFbpPart)).thenReturn(secondPart);

        LegQuotingData result = victim.convert(leg, secondFbpPart, BET_ID);

        assertNotNull(result);
        // Market based legs are expanded into one CBD Leg per FBP Part, so each leg only carries its own Part.
        assertEquals(result.getPartsList(), List.of(secondPart));
        verify(partConverter, never()).convert(firstFbpPart);
    }

    @Test
    public void testConvertWithNullPart() {
        LegOuterClass.Leg leg = LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .build();

        LegQuotingData result = victim.convert(leg, null, BET_ID);

        assertNotNull(result);
        assertEquals(result.getSelectionId(), 0L);
        assertEquals(result.getPartsList().size(), 0);
        assertEquals(result.getLegNumber(), 1);
        assertEquals(result.getLegStatus(), LegStatus.UNKNOWN_LEG_STATUS);
        assertEquals(result.getMarketId(), StringValue.of(""));
        assertEquals(result.getRule4Deductions(), 0d);
        assertEquals(result.getDeadHeatWinDeductions(), 0d);
        assertEquals(result.getDeadHeatEachwayDeductions(), 0d);
        assertEquals(result.getCombinationNumber(), StringValue.of(""));
        assertEquals(result.getEachwayDecimalFactor(), 0d);
        assertEquals(result.getEachwayOffset(), 0);
        // Neither the leg nor the (absent) part carries a price, so there is nothing to write.
        verify(fractionalPriceWriter).write(isNull(), any(LegQuotingData.Builder.class));
    }

    @Nonnull
    private static PartOuterClass.Part buildSelectionPart(String identifier, String id) {
        return PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(identifier, id)
                                .build())
                        .build())
                .build();
    }

    @Nonnull
    private static LegOuterClass.Leg getLeg() {
        PartOuterClass.Part part = PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "11122")
                                .build())
                        .build())
                .setResult(PartOuterClass.Part.Result.WON)
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "924.11")
                                .build())
                        .build())
                .setMarketTypeInfo(PartOuterClass.MarketTypeInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "MATCH_ODDS")
                                .build())
                        .build())
                .setFractionalPrice(FractionalPriceOuterClass.FractionalPrice.newBuilder()
                        .setCurrent(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(2)
                                .setDenominator(3)
                                .build())
                        .setMarginated(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(1)
                                .setDenominator(2)
                                .build())
                        .setStarting(FractionOuterClass.Fraction.newBuilder()
                                .setNumerator(1)
                                .setDenominator(2)
                                .build())
                        .setRaw(FractionOuterClass.Fraction.newBuilder()
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
                        .setEachwayPlaceOffset(Int32Value.of(2))
                        .build())
                .setNoCombi(Int32Value.of(5))
                .build();

        return LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .addPart(part)
                .build();
    }

}
