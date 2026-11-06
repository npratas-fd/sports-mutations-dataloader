package com.flutter.gbp.betstore.writer.convert.utils;

import com.google.protobuf.DoubleValue;
import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.google.common.base.Preconditions.checkArgument;
import static java.math.RoundingMode.HALF_UP;

public class SgmGroupPriceConverter {

    private static final BigDecimal DEFAULT_MINIMUM_VALID_PRICE = BigDecimal.valueOf(1.01);
    private static final int DEFAULT_HIGH_ROUNDING_SCALE = 2;
    private static final int DEFAULT_LOW_ROUNDING_SCALE = 3;
    static final int DEFAULT_FRACTIONAL_SCALE = 20;
    static final RoundingMode DEFAULT_FRACTIONAL_ROUNDING_MODE_STRATEGY = HALF_UP;

    private final BooleanSupplier roundStartPriceHalfUpToggle;

    public SgmGroupPriceConverter(BooleanSupplier roundStartPriceHalfUpToggle) {
        checkArgument(roundStartPriceHalfUpToggle != null, "roundStartPriceHalfUpToggle must be provided");
        this.roundStartPriceHalfUpToggle = roundStartPriceHalfUpToggle;
    }

    @Nonnull
    public DoubleValue convertSgmGroupPrice(long numerator, long denominator) {
        BigDecimal bigDecimalNumerator = BigDecimal.valueOf(numerator).setScale(DEFAULT_FRACTIONAL_SCALE);
        BigDecimal bigDecimalDenominator = BigDecimal.valueOf(denominator)
                .setScale(DEFAULT_FRACTIONAL_SCALE, DEFAULT_FRACTIONAL_ROUNDING_MODE_STRATEGY);
        BigDecimal decimalPrice = bigDecimalNumerator.add(bigDecimalDenominator)
                .divide(bigDecimalDenominator, DEFAULT_FRACTIONAL_ROUNDING_MODE_STRATEGY);
        return convertSgmGroupPrice(decimalPrice);
    }

    @Nonnull
    public DoubleValue convertSgmGroupPrice(double price) {
        return convertSgmGroupPrice(BigDecimal.valueOf(price));
    }

    @Nonnull
    private DoubleValue convertSgmGroupPrice(@Nonnull BigDecimal decimalPrice) {
        return DoubleValue.of(
                decimalPrice.setScale(getScale(decimalPrice), getRoundingMode()).doubleValue()
        );
    }

    private RoundingMode getRoundingMode() {
        return roundStartPriceHalfUpToggle.getAsBoolean() ? RoundingMode.HALF_UP : RoundingMode.DOWN;
    }

    private int getScale(BigDecimal price) {
        return price.compareTo(DEFAULT_MINIMUM_VALID_PRICE) >= 0 ?
                DEFAULT_HIGH_ROUNDING_SCALE : DEFAULT_LOW_ROUNDING_SCALE;
    }

}
