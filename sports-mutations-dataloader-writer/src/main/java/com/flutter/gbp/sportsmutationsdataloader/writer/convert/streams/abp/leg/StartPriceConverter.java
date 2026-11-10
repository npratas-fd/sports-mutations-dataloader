package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils.AbstractStartPriceConverter;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;
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
