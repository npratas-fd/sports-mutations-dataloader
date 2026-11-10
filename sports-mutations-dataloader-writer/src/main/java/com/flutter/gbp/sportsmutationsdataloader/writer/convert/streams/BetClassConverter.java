package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.BetClass;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class BetClassConverter<T extends Enum<T>> implements Converter<T, BetClass> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetClassConverter.class);
    private final Map<String, BetClass> betClassMappings = getBetClassMappings();

    @Override
    @Nonnull
    public BetClass convert(@Nullable T source) {
        if (source == null) {
            return BetClass.UNKNOWN_BET_CLASS;
        }
        BetClass target = betClassMappings.get(source.name());
        if (target == null) {
            LOGGER.warn("Exception: Failed to map unknown leg bet type '{}' to BetClass.", source);
            return BetClass.UNKNOWN_BET_CLASS;
        }
        return target;
    }

    private Map<String, BetClass> getBetClassMappings() {
        return new ImmutableMap.Builder<String, BetClass>()
                .put("UNKNOWN_LEG_BET_TYPE", BetClass.UNKNOWN_BET_CLASS)
                .put("WIN", BetClass.WIN)
                .put("EACHWAY", BetClass.EACHWAY)
                .build();
    }
}