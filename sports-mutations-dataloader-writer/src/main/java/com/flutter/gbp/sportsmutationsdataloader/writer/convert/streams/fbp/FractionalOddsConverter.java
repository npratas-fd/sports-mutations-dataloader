package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;

import javax.annotation.Nonnull;

public class FractionalOddsConverter implements Converter<FractionOuterClass.Fraction, FractionalOdds> {
    @Override
    public FractionalOdds convert(@Nonnull FractionOuterClass.Fraction source) {
        return FractionalOdds.newBuilder()
                .setNumerator(Int32Value.of(source.getNumerator()))
                .setDenominator(Int32Value.of(source.getDenominator()))
                .build();
    }
}