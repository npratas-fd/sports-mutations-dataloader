package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.leg;

import com.flutter.gbp.fcq.bet.leg.Part;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class PartConverterTest {

    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String CHOICE_IDENTIFIER = "CHOICE";
    private static final String GBP_IDENTIFIER = "GBP";
    private static final String RAMP_IDENTIFIER = "RAMP";

    private final PartConverter victim = new PartConverter();

    @Test
    public void testConvertNullReturnsNull() {
        assertNull(victim.convert(null));
    }

    @Test
    public void testConvertEmptyPartReturnsNull() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder().build();
        assertNull(victim.convert(fbpPart));
    }

    @Test
    public void testConvertEmptyIdMapsReturnsNull() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().build())
                        .build())
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().build())
                        .build())
                .build();
        assertNull(victim.convert(fbpPart));
    }

    @Test
    public void testConvertEmsPart() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "1.2345")
                                .build())
                        .build())
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "12345")
                                .build())
                        .build())
                .build();

        Part result = victim.convert(fbpPart);
        assertNotNull(result);
        assertTrue(result.hasMarketId());
        assertEquals(result.getMarketId(), "1.2345");
        assertTrue(result.hasSelectionId());
        assertEquals(result.getSelectionId(), "12345");
        assertFalse(result.hasChoiceIdRef());
    }

    @Test
    public void testConvertSgxPartAllThreeFields() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "1.100")
                                .build())
                        .build())
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "99")
                                .putIds(CHOICE_IDENTIFIER, "choice-1")
                                .build())
                        .build())
                .build();

        Part result = victim.convert(fbpPart);
        assertNotNull(result);
        assertEquals(result.getMarketId(), "1.100");
        assertEquals(result.getSelectionId(), "99");
        assertEquals(result.getChoiceIdRef(), "choice-1");
    }

    @Test
    public void testConvertObpStubPartOnlyChoiceIdRef() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(CHOICE_IDENTIFIER, "obp-choice")
                                .build())
                        .build())
                .build();

        Part result = victim.convert(fbpPart);
        assertNotNull(result);
        assertFalse(result.hasMarketId());
        assertFalse(result.hasSelectionId());
        assertEquals(result.getChoiceIdRef(), "obp-choice");
    }

    @Test
    public void testConvertPreservesNonNumericSelectionId() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "gpd:non-numeric")
                                .build())
                        .build())
                .build();

        Part result = victim.convert(fbpPart);
        assertNotNull(result);
        assertEquals(result.getSelectionId(), "gpd:non-numeric");
    }

    @Test
    public void testConvertMarketType() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketTypeInfo(PartOuterClass.MarketTypeInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "MATCH_ODDS")
                                .build())
                        .build())
                .build();

        Part result = victim.convert(fbpPart);
        assertNotNull(result);
        assertTrue(result.hasMarketType());
        assertEquals(result.getMarketType(), "MATCH_ODDS");
    }

    @Test
    public void testEmptyStringValueTreatedAsAbsent() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "")
                                .build())
                        .build())
                .build();
        assertNull(victim.convert(fbpPart));
    }

    @Test
    public void testConvertMarketFeedsIdStripsGbpPrefix() {
        Part result = victim.convert(emsPartWithGbpMarketId("gpd:123456"));

        assertNotNull(result);
        assertEquals(result.getMarketId(), "1.2345");
        assertTrue(result.hasOriginalMarketFeedsId());
        assertEquals(result.getOriginalMarketFeedsId(), "123456");
    }

    @Test
    public void testConvertMarketFeedsIdWithoutPrefixIsKeptAsIs() {
        Part result = victim.convert(emsPartWithGbpMarketId("123456"));

        assertNotNull(result);
        assertEquals(result.getOriginalMarketFeedsId(), "123456");
    }

    @Test
    public void testConvertMarketFeedsIdAbsentWhenNoGbpMarketId() {
        Part result = victim.convert(emsPartWithGbpMarketId(null));

        assertNotNull(result);
        assertFalse(result.hasOriginalMarketFeedsId());
    }

    @Test
    public void testConvertMarketFeedsIdAbsentWhenGbpMarketIdIsEmptyOrOnlyPrefix() {
        assertFalse(victim.convert(emsPartWithGbpMarketId("")).hasOriginalMarketFeedsId());
        assertFalse(victim.convert(emsPartWithGbpMarketId("gpd:")).hasOriginalMarketFeedsId());
    }

    @Test
    public void testConvertGbpMarketIdAloneDoesNotProduceAPart() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(GBP_IDENTIFIER, "gpd:123456")
                                .build())
                        .build())
                .build();

        assertNull(victim.convert(fbpPart));
    }

    @Test
    public void testConvertStoresRampIdsExactlyAsReceived() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "1.2345")
                                .putIds(RAMP_IDENTIFIER, "gpd:market-ramp")))
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "12345")
                                .putIds(RAMP_IDENTIFIER, "12345_ramp")))
                .build();

        Part result = victim.convert(fbpPart);

        assertNotNull(result);
        assertEquals(result.getMarketId(), "1.2345");
        assertEquals(result.getSelectionId(), "12345");
        assertTrue(result.hasOriginalMarketRampId());
        assertEquals(result.getOriginalMarketRampId(), "gpd:market-ramp");
        assertTrue(result.hasOriginalSelectionRampId());
        assertEquals(result.getOriginalSelectionRampId(), "12345_ramp");
    }

    @Test
    public void testConvertRampIdsAbsentWhenNotReceived() {
        Part result = victim.convert(emsPartWithGbpMarketId(null));

        assertNotNull(result);
        assertFalse(result.hasOriginalMarketRampId());
        assertFalse(result.hasOriginalSelectionRampId());
    }

    @Test
    public void testConvertEmptyRampIdsAreAbsent() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTSBOOK_IDENTIFIER, "1.2345")
                                .putIds(RAMP_IDENTIFIER, "")))
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().putIds(RAMP_IDENTIFIER, "")))
                .build();

        Part result = victim.convert(fbpPart);

        assertNotNull(result);
        assertEquals(result.getMarketId(), "1.2345");
        assertFalse(result.hasOriginalMarketRampId());
        assertFalse(result.hasOriginalSelectionRampId());
    }

    @Test
    public void testConvertRampIdsAloneDoNotProduceAPart() {
        PartOuterClass.Part fbpPart = PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().putIds(RAMP_IDENTIFIER, "market-ramp")))
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder().putIds(RAMP_IDENTIFIER, "selection-ramp")))
                .build();

        assertNull(victim.convert(fbpPart));
    }

    private PartOuterClass.Part emsPartWithGbpMarketId(String gbpMarketId) {
        PartOuterClass.Entity.Builder marketEntity = PartOuterClass.Entity.newBuilder()
                .putIds(SPORTSBOOK_IDENTIFIER, "1.2345");
        if (gbpMarketId != null) {
            marketEntity.putIds(GBP_IDENTIFIER, gbpMarketId);
        }
        return PartOuterClass.Part.newBuilder()
                .setMarketInfo(PartOuterClass.MarketInfo.newBuilder().setEntity(marketEntity.build()).build())
                .setSelectionInfo(PartOuterClass.SelectionInfo.newBuilder()
                        .setEntity(PartOuterClass.Entity.newBuilder()
                                .putIds(SPORTEX_IDENTIFIER, "12345")
                                .build())
                        .build())
                .build();
    }
}
