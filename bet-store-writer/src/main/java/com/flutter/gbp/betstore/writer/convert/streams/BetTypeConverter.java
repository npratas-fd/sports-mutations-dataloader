package com.flutter.gbp.betstore.writer.convert.streams;

import com.flutter.gbp.betstore.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.BetType;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class BetTypeConverter<T extends Enum<T>> implements Converter<T, BetType> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetTypeConverter.class);
    private final Map<String, BetType> betTypeMappings = getBetTypeMappings();

    @Override
    @Nonnull
    public BetType convert(@Nullable T source) {
        if (source == null) {
            return BetType.UNKNOWN_BET_TYPE;
        }
        BetType target = betTypeMappings.get(source.name());
        if (target == null) {
            LOGGER.warn("Exception: Failed to map unknown bet type '{}' to BetType.", source);
            return BetType.UNKNOWN_BET_TYPE;
        }
        return target;
    }

    private Map<String, BetType> getBetTypeMappings() {
        return new ImmutableMap.Builder<String, BetType>()
                .put("UNKNOWN_BET_TYPE", BetType.UNKNOWN_BET_TYPE)
                .put("SINGLE", BetType.SINGLE)
                .put("DOUBLE", BetType.DOUBLE)
                .put("TREBLE", BetType.TREBLE)
                .put("FOURFOLD", BetType.FOURFOLD)
                .put("FIVEFOLD", BetType.FIVEFOLD)
                .put("SIXFOLD", BetType.SIXFOLD)
                .put("SEVENFOLD", BetType.SEVENFOLD)
                .put("EIGHTFOLD", BetType.EIGHTFOLD)
                .put("NINEFOLD", BetType.NINEFOLD)
                .put("TENFOLD", BetType.TENFOLD)
                .put("ELEVENFOLD", BetType.ELEVENFOLD)
                .put("TWELVEFOLD", BetType.TWELVEFOLD)
                .put("THIRTEENFOLD", BetType.THIRTEENFOLD)
                .put("FOURTEENFOLD", BetType.FOURTEENFOLD)
                .put("FIFTEENFOLD", BetType.FIFTEENFOLD)
                .put("SIXTEENFOLD", BetType.SIXTEENFOLD)
                .put("SEVENTEENFOLD", BetType.SEVENTEENFOLD)
                .put("EIGHTEENFOLD", BetType.EIGHTEENFOLD)
                .put("NINETEENFOLD", BetType.NINETEENFOLD)
                .put("TWENTYFOLD", BetType.TWENTYFOLD)
                .put("TWENTYONEFOLD", BetType.TWENTYONEFOLD)
                .put("TWENTYTWOFOLD", BetType.TWENTYTWOFOLD)
                .put("TWENTYTHREEFOLD", BetType.TWENTYTHREEFOLD)
                .put("TWENTYFOURFOLD", BetType.TWENTYFOURFOLD)
                .put("TWENTYFIVEFOLD", BetType.TWENTYFIVEFOLD)
                .put("TRIXIE", BetType.TRIXIE)
                .put("PATENT", BetType.PATENT)
                .put("YANKEE", BetType.YANKEE)
                .put("CANADIAN", BetType.CANADIAN)
                .put("HEINZ", BetType.HEINZ)
                .put("SUPER_HEINZ", BetType.SUPER_HEINZ)
                .put("GOLIATH", BetType.GOLIATH)
                .put("LUCKY_15", BetType.LUCKY_15)
                .put("LUCKY_31", BetType.LUCKY_31)
                .put("LUCKY_63", BetType.LUCKY_63)
                .build();
    }
}