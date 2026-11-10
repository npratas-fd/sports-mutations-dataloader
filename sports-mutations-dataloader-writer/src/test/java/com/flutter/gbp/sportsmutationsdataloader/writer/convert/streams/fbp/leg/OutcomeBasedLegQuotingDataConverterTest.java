package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.LegStatusConverter;
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

public class OutcomeBasedLegQuotingDataConverterTest {

    private static final String BET_ID = "O/123456789/0001";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";
    private static final String CHOICE_IDENTIFIER = "CHOICE";

    @Mock
    private LegStatusConverter<LegOuterClass.Leg.Result> legStatusConverter;

    @Mock
    private FbpFractionalPriceWriter fractionalPriceWriter;

    @Mock
    private PartConverter partConverter;

    @InjectMocks
    private OutcomeBasedLegQuotingDataConverterImpl victim;

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
    public void testConvertMapsOnlyTheLegNumberTheResultAndThePriceTerms() {
        // The leg built by getLeg() carries no fractionalPrice and a single part, so the price falls back to that
        // part's fractionalPrice.
        LegOuterClass.Leg leg = getLeg();
        Part stubbedPart = Part.newBuilder()
                .setMarketId("stub-market")
                .setSelectionId("stub-selection")
                .build();

        when(legStatusConverter.convert(LegOuterClass.Leg.Result.WON)).thenReturn(LegStatus.WON);
        when(partConverter.convert(leg.getPart(0))).thenReturn(stubbedPart);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);

        // Legacy CBD proto marketId / selectionId are never written by this converter.
        assertEquals(result.getSelectionId(), 0L);
        assertFalse(result.hasMarketId());
        assertEquals(result.getMarketId(), StringValue.getDefaultInstance());

        // The converted Part returned by the (mocked) PartConverter is appended verbatim.
        assertEquals(result.getPartsList(), List.of(stubbedPart));

        // The leg number and the result both come from the leg.
        assertEquals(result.getLegNumber(), 1);
        assertEquals(result.getLegStatus(), LegStatus.WON);
        // marketType is Part state now and is no longer written at LegQuotingData level.
        assertFalse(result.hasMarketType());
        // The price fields themselves are written by the FbpFractionalPriceWriter, from the single part's price here.
        verify(fractionalPriceWriter).write(eq(leg.getPart(0).getFractionalPrice()), any(LegQuotingData.Builder.class));

        // The eachway, rule 4 and combination terms of the first Part are Part level state and are no longer
        // written at LegQuotingData level, even though the first Part carries all of them.
        assertEquals(result.getRule4Deductions(), 0d);
        assertEquals(result.getDeadHeatWinDeductions(), 0d);
        assertEquals(result.getDeadHeatEachwayDeductions(), 0d);
        assertFalse(result.hasCombinationNumber());
        assertEquals(result.getEachwayDecimalFactor(), 0d);
        assertEquals(result.getEachwayOffset(), 0);
    }

    @Test
    public void testConvertWritesTheLegFractionalPriceWhenPresentInsteadOfTheFirstPartFractionalPrice() {
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

        when(legStatusConverter.convert(LegOuterClass.Leg.Result.WON)).thenReturn(LegStatus.WON);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        // The combined leg price is the price of the whole multiple choice group and wins over the first part's own price.
        verify(fractionalPriceWriter).write(eq(legFractionalPrice), any(LegQuotingData.Builder.class));
        verify(fractionalPriceWriter, never())
                .write(eq(leg.getPart(0).getFractionalPrice()), any(LegQuotingData.Builder.class));
    }

    @Test
    public void testConvertTakesTheResultFromTheLegAndNotFromItsParts() {
        // The whole multiple choice group is resulted as one unit, so the Part results are not the leg result.
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(PartOuterClass.Part.newBuilder().setResult(PartOuterClass.Part.Result.LOST).build())
                .addPart(PartOuterClass.Part.newBuilder().setResult(PartOuterClass.Part.Result.VOIDED).build())
                .setResult(LegOuterClass.Leg.Result.WON)
                .build();

        when(legStatusConverter.convert(LegOuterClass.Leg.Result.WON)).thenReturn(LegStatus.WON);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        assertEquals(result.getLegStatus(), LegStatus.WON);
        verify(legStatusConverter).convert(LegOuterClass.Leg.Result.WON);
    }

    @Test
    public void testConvertWritesNoPriceWhenNeitherLegNorFirstPartHaveFractionalPrice() {
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(PartOuterClass.Part.getDefaultInstance())
                .setResult(LegOuterClass.Leg.Result.WON)
                .build();

        when(legStatusConverter.convert(LegOuterClass.Leg.Result.WON)).thenReturn(LegStatus.WON);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        verify(fractionalPriceWriter).write(isNull(), any(LegQuotingData.Builder.class));
    }

    @Test
    public void testConvertWritesNoPriceWhenTheLegCarriesNoPriceAndMoreThanOnePart() {
        // The first part carries a full FractionalPrice, but it prices only that part of the multiple choice group.
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(getLeg().getPart(0))
                .addPart(buildSelectionPart(CHOICE_IDENTIFIER, "choice-1"))
                .setResult(LegOuterClass.Leg.Result.WON)
                .build();

        when(legStatusConverter.convert(LegOuterClass.Leg.Result.WON)).thenReturn(LegStatus.WON);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        // No individual part price represents the combined price of the group, so no price at all is resolved.
        verify(fractionalPriceWriter).write(isNull(), any(LegQuotingData.Builder.class));
    }

    @Test
    public void testConvertAppendsEveryPartOfTheLeg() {
        PartOuterClass.Part emsFbpPart = buildSelectionPart(SPORTEX_IDENTIFIER, "11122");
        PartOuterClass.Part obpNativeFbpPart = buildSelectionPart(CHOICE_IDENTIFIER, "choice-1");
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(emsFbpPart)
                .addPart(obpNativeFbpPart)
                .build();

        stubScalarConverters();
        Part emsPart = Part.newBuilder().setMarketId("1.2345").setSelectionId("11122").build();
        Part obpNativePart = Part.newBuilder().setChoiceIdRef("choice-1").build();
        when(partConverter.convert(emsFbpPart)).thenReturn(emsPart);
        when(partConverter.convert(obpNativeFbpPart)).thenReturn(obpNativePart);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        // Both the EMS market/selection Part and the OBP-native choice Part land on the single converted leg, in order.
        assertEquals(result.getPartsList(), List.of(emsPart, obpNativePart));
    }

    @Test
    public void testConvertSkipsUnidentifiedParts() {
        PartOuterClass.Part identifiedFbpPart = buildSelectionPart(CHOICE_IDENTIFIER, "choice-1");
        PartOuterClass.Part unidentifiedFbpPart = PartOuterClass.Part.getDefaultInstance();
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(identifiedFbpPart)
                .addPart(unidentifiedFbpPart)
                .build();

        stubScalarConverters();
        Part convertedPart = Part.newBuilder().setChoiceIdRef("choice-1").build();
        when(partConverter.convert(identifiedFbpPart)).thenReturn(convertedPart);
        when(partConverter.convert(unidentifiedFbpPart)).thenReturn(null);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        assertEquals(result.getPartsList(), List.of(convertedPart));
    }

    @Test
    public void testConvertAppendsNoPartsWhenEveryPartIsUnidentified() {
        PartOuterClass.Part firstUnidentifiedFbpPart = PartOuterClass.Part.getDefaultInstance();
        PartOuterClass.Part secondUnidentifiedFbpPart = PartOuterClass.Part.newBuilder()
                .setResult(PartOuterClass.Part.Result.WON)
                .build();
        LegOuterClass.Leg leg = outcomeBasedLegBuilder()
                .addPart(firstUnidentifiedFbpPart)
                .addPart(secondUnidentifiedFbpPart)
                .build();

        stubScalarConverters();
        when(partConverter.convert(firstUnidentifiedFbpPart)).thenReturn(null);
        when(partConverter.convert(secondUnidentifiedFbpPart)).thenReturn(null);

        LegQuotingData result = victim.convert(leg, BET_ID);

        assertNotNull(result);
        assertEquals(result.getPartsList(), List.of());
    }

    @Test
    public void testConvertWithoutParts() {
        LegOuterClass.Leg leg = outcomeBasedLegBuilder().build();

        when(legStatusConverter.convert(LegOuterClass.Leg.Result.NONE)).thenReturn(LegStatus.UNKNOWN_LEG_STATUS);

        LegQuotingData result = victim.convert(leg, BET_ID);

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
    }

    private void stubScalarConverters() {
        when(legStatusConverter.convert(any())).thenReturn(LegStatus.UNKNOWN_LEG_STATUS);
    }

    @Nonnull
    private static LegOuterClass.Leg.Builder outcomeBasedLegBuilder() {
        return LegOuterClass.Leg.newBuilder()
                .setLegNumber(1)
                .setLegType(LegOuterClass.Leg.LegType.OUTCOME_BASED);
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
                .setResult(PartOuterClass.Part.Result.LOST)
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

        // The leg result is WON while the Part is LOST: the result of the group is read from the leg, not the Part.
        return outcomeBasedLegBuilder()
                .addPart(part)
                .setResult(LegOuterClass.Leg.Result.WON)
                .build();
    }

}
