package com.flutter.gbp.betstore.writer.convert.streams.fbp;

import com.flutter.gbp.fcq.bet.odds.DecimalOdds;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.flutter.gbp.fcq.bet.odds.TrueOdds;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.fbp.proto.bet.CombinedPriceOuterClass;
import com.ppb.platform.sb.fbp.proto.bet.FractionOuterClass;

import javax.annotation.Nonnull;

public class OddsConverter {

    public Odds convert(@Nonnull CombinedPriceOuterClass.CombinedPrice source) {
        DecimalOdds decimalOdds = DecimalOdds.newBuilder()
                .setDecimalOdds(DoubleValue.of(source.getDecimal()))
                .build();

        FractionalOdds.Builder fractionalOddsBuilder = FractionalOdds.newBuilder();

        if (source.hasFractional()) {
            fractionalOddsBuilder
                    .setNumerator(Int32Value.of(source.getFractional().getNumerator()))
                    .setDenominator(Int32Value.of(source.getFractional().getDenominator()));
        }

        TrueOdds trueOdds = TrueOdds.newBuilder()
                .setDecimalOdds(decimalOdds)
                .setFractionalOdds(fractionalOddsBuilder.build())
                .build();

        return Odds.newBuilder()
                .setTrueOdds(trueOdds)
                .build();
    }

    public Odds convert(@Nonnull FractionOuterClass.Fraction source) {

        FractionalOdds.Builder fractionalOddsBuilder = FractionalOdds.newBuilder()
                .setNumerator(Int32Value.of(source.getNumerator()))
                .setDenominator(Int32Value.of(source.getDenominator()));

        TrueOdds trueOdds = TrueOdds.newBuilder()
                .setFractionalOdds(fractionalOddsBuilder.build())
                .build();

        return Odds.newBuilder()
                .setTrueOdds(trueOdds)
                .build();
    }
}
