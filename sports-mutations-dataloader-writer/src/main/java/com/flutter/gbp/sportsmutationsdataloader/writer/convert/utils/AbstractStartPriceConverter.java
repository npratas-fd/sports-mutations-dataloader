package com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils;

import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.google.common.base.Preconditions.checkArgument;
import static java.math.RoundingMode.HALF_UP;

public abstract class AbstractStartPriceConverter<T> {
    private final BooleanSupplier roundStartPriceHalfUpToggle;

    protected AbstractStartPriceConverter(BooleanSupplier roundStartPriceHalfUpToggle) {
        checkArgument(roundStartPriceHalfUpToggle != null, "roundStartPriceHalfUpToggle must be provided");
        this.roundStartPriceHalfUpToggle = roundStartPriceHalfUpToggle;
    }

    protected abstract Double convertStartPrice(@Nonnull T fraction);

    protected Double decimalOdds(int numerator, int denominator) {
        BigDecimal bigDecimalNumerator = BigDecimal.valueOf(numerator).setScale(20);
        BigDecimal bigDecimalDenominator = BigDecimal.valueOf(denominator).setScale(20, HALF_UP);
        BigDecimal decimalPrice = bigDecimalNumerator.add(bigDecimalDenominator).divide(bigDecimalDenominator, HALF_UP);

        RoundingMode roundingMode = roundStartPriceHalfUpToggle.getAsBoolean()
                ? RoundingMode.HALF_UP
                : RoundingMode.DOWN;
        int scale = decimalPrice.compareTo(BigDecimal.valueOf(1.01)) >= 0 ? 2 : 3;
        BigDecimal price = decimalPrice.setScale(scale, roundingMode);

        return Double.parseDouble(price.stripTrailingZeros().toPlainString());
    }
}
