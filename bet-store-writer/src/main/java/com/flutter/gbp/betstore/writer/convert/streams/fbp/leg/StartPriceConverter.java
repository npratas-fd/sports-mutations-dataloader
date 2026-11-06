package com.flutter.gbp.betstore.writer.convert.streams.fbp.leg;

import com.flutter.gbp.betstore.writer.convert.utils.AbstractStartPriceConverter;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;
import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;

public class StartPriceConverter extends AbstractStartPriceConverter<FractionOuterClass.Fraction> {

    public StartPriceConverter(BooleanSupplier roundStartPriceHalfUpToggle) {
        super(roundStartPriceHalfUpToggle);
    }

    @Override
    public Double convertStartPrice(@Nonnull FractionOuterClass.Fraction startPriceFractional) {
        return decimalOdds(startPriceFractional.getNumerator(), startPriceFractional.getDenominator());
    }
}
