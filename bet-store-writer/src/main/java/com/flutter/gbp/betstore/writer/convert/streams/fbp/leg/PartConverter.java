package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.fcq.bet.leg.Part;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Converts one FBP {@link PartOuterClass.Part} to one CBD proto {@link Part}.
 * <p>
 * Field population is driven by what keys are present in the FBP Part's entity id maps:
 * All IDs are stored as strings without numeric coercion, preserving non-numeric upstream values.
 * <p>
 * The Part also carries the market type, which is resolved per selection rather than per leg.
 * <p>
 * The market feeds ID is read from the {@code GBP} market id (e.g. {@code gpd:123}) with the {@code gpd:} prefix removed.
 * The market and selection RAMP IDs are read from the {@code RAMP} market and selection ids exactly as received.
 * These identifiers are only attached to a Part that is otherwise kept: on their own they do not make a Part worth storing.
 */
public class PartConverter {

    private static final String SPORTSBOOK_IDENTIFIER = "SPORTSBOOK";
    private static final String SPORTEX_IDENTIFIER = "SPORTEX";
    private static final String CHOICE_IDENTIFIER = "CHOICE";
    private static final String GBP_IDENTIFIER = "GBP";
    private static final String GBP_ID_PREFIX = "gpd:";
    private static final String RAMP_IDENTIFIER = "RAMP";

    /**
     * Converts an FBP Part to a CBD proto Part.
     *
     * @param fbpPart the FBP Part to convert; returns null if the Part has no identifiers to store
     * @return a CBD proto Part, or null if the FBP Part has none of marketId, selectionId, choiceIdRef or marketType;
     *         the market feeds ID and RAMP IDs alone do not produce a Part
     */
    @Nullable
    public Part convert(PartOuterClass.Part fbpPart) {
        if (fbpPart == null) {
            return null;
        }

        String marketId = extractFromMarketIds(fbpPart, SPORTSBOOK_IDENTIFIER);
        String selectionId = extractFromSelectionIds(fbpPart, SPORTEX_IDENTIFIER);
        String choiceIdRef = extractFromSelectionIds(fbpPart, CHOICE_IDENTIFIER);
        String marketType = extractMarketType(fbpPart);

        if (marketId == null && selectionId == null && choiceIdRef == null && marketType == null) {
            return null;
        }

        Part.Builder builder = Part.newBuilder();
        if (marketId != null) {
            builder.setMarketId(marketId);
        }
        if (selectionId != null) {
            builder.setSelectionId(selectionId);
        }
        if (choiceIdRef != null) {
            builder.setChoiceIdRef(choiceIdRef);
        }
        if (marketType != null) {
            builder.setMarketType(marketType);
        }
        String marketFeedsId = extractMarketFeedsId(fbpPart);
        if (marketFeedsId != null) {
            builder.setOriginalMarketFeedsId(marketFeedsId);
        }
        String marketRampId = extractFromMarketIds(fbpPart, RAMP_IDENTIFIER);
        if (marketRampId != null) {
            builder.setOriginalMarketRampId(marketRampId);
        }
        String selectionRampId = extractFromSelectionIds(fbpPart, RAMP_IDENTIFIER);
        if (selectionRampId != null) {
            builder.setOriginalSelectionRampId(selectionRampId);
        }
        return builder.build();
    }

    /**
     * Returns the GBP market id without its {@code gpd:} prefix, or null if absent or empty once the prefix is removed.
     * Values without the prefix are kept as they are.
     */
    @Nullable
    private String extractMarketFeedsId(PartOuterClass.Part part) {
        String gbpMarketId = extractFromMarketIds(part, GBP_IDENTIFIER);
        if (gbpMarketId == null) {
            return null;
        }
        String marketFeedsId = gbpMarketId.startsWith(GBP_ID_PREFIX) ? gbpMarketId.substring(GBP_ID_PREFIX.length()) : gbpMarketId;
        return marketFeedsId.isEmpty() ? null : marketFeedsId;
    }

    @Nullable
    private String extractFromMarketIds(PartOuterClass.Part part, String key) {
        if (!part.hasMarketInfo() || !part.getMarketInfo().hasEntity()) {
            return null;
        }
        return getNonEmpty(part.getMarketInfo().getEntity().getIdsMap(), key);
    }

    @Nullable
    private String extractMarketType(PartOuterClass.Part part) {
        if (!part.hasMarketTypeInfo() || !part.getMarketTypeInfo().hasEntity()) {
            return null;
        }
        return getNonEmpty(part.getMarketTypeInfo().getEntity().getIdsMap(), SPORTSBOOK_IDENTIFIER);
    }

    @Nullable
    private String extractFromSelectionIds(PartOuterClass.Part part, String key) {
        if (!part.hasSelectionInfo() || !part.getSelectionInfo().hasEntity()) {
            return null;
        }
        return getNonEmpty(part.getSelectionInfo().getEntity().getIdsMap(), key);
    }

    @Nullable
    private String getNonEmpty(Map<String, String> ids, String key) {
        String value = ids.get(key);
        return (value != null && !value.isEmpty()) ? value : null;
    }
}
