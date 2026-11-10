package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.PriceTypeConverter;
import com.flutter.gbp.fcq.bet.Price;
import com.flutter.gbp.fcq.bet.leg.LegQuotingData;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Writes the price related LegQuotingData fields from the FBP FractionalPrice that prices a leg.
 * <p>
 * The same price fields are populated the same way for every leg type; only which FractionalPrice prices the leg
 * differs, and that is resolved by {@link FbpFractionalPriceResolver}.
 */
public class FbpFractionalPriceWriter {

    private final StartPriceConverter startPriceConverter;
    private final PriceTypeConverter priceTypeConverter;

    public FbpFractionalPriceWriter(StartPriceConverter startPriceConverter, PriceTypeConverter priceTypeConverter) {
        checkArgument(startPriceConverter != null, "startPriceConverter cannot be null");
        checkArgument(priceTypeConverter != null, "priceTypeConverter cannot be null");

        this.startPriceConverter = startPriceConverter;
        this.priceTypeConverter = priceTypeConverter;
    }

    /**
     * Populates the price related fields of the leg quoting data being built. Each price field is left untouched when
     * the FractionalPrice does not carry the fraction it comes from, so that a partially populated upstream price does
     * not overwrite the proto defaults with zeroed prices.
     *
     * @param fractionalPrice the FractionalPrice pricing the leg, as resolved by {@link FbpFractionalPriceResolver};
     *                        when {@code null} no price field is written
     * @param builder the target LegQuotingData builder
     */
    public void write(@Nullable FractionalPriceOuterClass.FractionalPrice fractionalPrice, @Nonnull LegQuotingData.Builder builder) {
        if (fractionalPrice == null) {
            return;
        }
        if (fractionalPrice.hasCurrent()) {
            builder.setPrice(convertFractionToPrice(fractionalPrice.getCurrent()));
        }
        if (fractionalPrice.hasMarginated()) {
            builder.setOriginalPrice(convertFractionToPrice(fractionalPrice.getMarginated()));
        }
        if (fractionalPrice.hasStarting()) {
            builder.setStartPrice(startPriceConverter.convertStartPrice(fractionalPrice.getStarting()));
        }
        if (fractionalPrice.hasRaw()) {
            builder.setOriginalBasePrice(convertFractionToPrice(fractionalPrice.getRaw()));
        }
        builder.setPriceType(priceTypeConverter.convert(fractionalPrice.getType()));
    }

    private Price convertFractionToPrice(@Nonnull FractionOuterClass.Fraction fraction) {
        return Price.newBuilder()
                .setNumerator(fraction.getNumerator())
                .setDenominator(fraction.getDenominator())
                .build();
    }
}
