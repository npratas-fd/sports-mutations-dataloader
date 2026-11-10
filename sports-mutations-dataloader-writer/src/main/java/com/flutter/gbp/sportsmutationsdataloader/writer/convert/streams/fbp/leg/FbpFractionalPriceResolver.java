package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.leg;

import com.ppb.platform.sb.fbp.proto.bet.leg.LegOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.PartOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Resolves which FBP FractionalPrice prices a converted CBD leg.
 * <p>
 * FBP is moving towards publishing a single combined price on the leg itself, which is the source of truth once
 * published, so the leg's price always takes precedence. The Part level price remains a fallback for as long as FBP
 * has not started publishing {@code leg.fractionalPrice} in production, and this whole fallback goes away once it
 * does.
 * <p>
 * Which Part may stand in for the leg depends on what the leg is: see {@link #resolveForPart} for market-based legs
 * and {@link #resolveForGroup} for outcome-based ones.
 */
public final class FbpFractionalPriceResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FbpFractionalPriceResolver.class);

    private FbpFractionalPriceResolver() {
    }

    /**
     * Resolves the price of the CBD leg converted from one Part of a market-based leg, where each Part is an
     * independent selection carrying its own price.
     *
     * @param leg the FBP leg being converted; may be {@code null}
     * @param part the FBP Part the CBD leg is being converted from; may be {@code null}
     * @return the leg's combined price, the Part's own price when the leg carries none, or {@code null} when neither
     *         carries one
     */
    @Nullable
    public static FractionalPriceOuterClass.FractionalPrice resolveForPart(@Nullable LegOuterClass.Leg leg,
                                                                          @Nullable PartOuterClass.Part part) {
        if (leg != null && leg.hasFractionalPrice()) {
            return leg.getFractionalPrice();
        }
        if (part != null && part.hasFractionalPrice()) {
            return part.getFractionalPrice();
        }
        return null;
    }

    /**
     * Resolves the price of the single CBD leg converted from an outcome-based leg, i.e. from a multiple choice group
     * quoted as one unit. The combined leg price is the price of that whole group and is mandatory for such legs.
     * <p>
     * The fallback to the Part price is only valid when the leg carries a single Part, as that Part's price is then
     * the price of the whole group. When the leg carries more than one Part no price is resolved, because no
     * individual Part price represents the combined price of the group.
     *
     * @param leg the FBP leg being converted; may be {@code null}
     * @param betId the id of the bet being converted, used only to correlate the warning logged when no price can be
     *              resolved; leg numbers are bet scoped, so they do not identify a leg on their own
     * @return the leg's combined price, its single Part's price when the leg carries none, or {@code null} when
     *         neither carries one or the leg carries more than one Part
     */
    @Nullable
    public static FractionalPriceOuterClass.FractionalPrice resolveForGroup(@Nullable LegOuterClass.Leg leg, @Nullable String betId) {
        if (leg == null) {
            return null;
        }
        if (leg.hasFractionalPrice()) {
            return leg.getFractionalPrice();
        }
        if (leg.getPartCount() > 1) {
            LOGGER.warn("operation=resolveForGroup, msg='Leg carries no combined fractional price and more than one Part, "
                    + "no price resolved.', betId={}, legNumber={}, partCount={}", betId, leg.getLegNumber(), leg.getPartCount());
            return null;
        }
        return leg.getPartList().stream()
                .findFirst()
                .filter(PartOuterClass.Part::hasFractionalPrice)
                .map(PartOuterClass.Part::getFractionalPrice)
                .orElse(null);
    }
}
