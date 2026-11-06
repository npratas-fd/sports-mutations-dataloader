package com.flutter.gbp.betstore.writer.convert.streams.abp;

import com.flutter.gbp.fcq.bet.odds.DecimalOdds;
import com.flutter.gbp.fcq.bet.odds.FractionalOdds;
import com.flutter.gbp.fcq.bet.odds.Odds;
import com.flutter.gbp.fcq.bet.odds.TrueOdds;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.ppb.platform.sb.retail.proto.bet.FractionOuterClass;

import javax.annotation.Nonnull;

public class OddsConverter {

    public Odds convert(@Nonnull FractionOuterClass.Fraction source) {
        FractionalOdds fractionalOdds = FractionalOdds.newBuilder()
                .setNumerator(Int32Value.of(source.getNumerator()))
                .setDenominator(Int32Value.of(source.getDenominator()))
                .build();
        TrueOdds trueOdds = TrueOdds.newBuilder()
                .setFractionalOdds(fractionalOdds)
                .build();
        return Odds.newBuilder()
                .setTrueOdds(trueOdds)
                .build();
    }

    public Odds convert(double source) {
        DecimalOdds decimalOdds = DecimalOdds.newBuilder()
                .setDecimalOdds(DoubleValue.of(source))
                .build();
        TrueOdds trueOdds = TrueOdds.newBuilder()
                .setDecimalOdds(decimalOdds)
                .build();
        return Odds.newBuilder()
                .setTrueOdds(trueOdds)
                .build();
    }
}
