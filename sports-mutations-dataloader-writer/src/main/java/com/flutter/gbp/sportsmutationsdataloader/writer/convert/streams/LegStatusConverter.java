package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.leg.LegStatus;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class LegStatusConverter<T extends Enum<T>> implements Converter<T, LegStatus> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegStatusConverter.class);
    private final Map<String, LegStatus> legStatusMappings = getLegStatusMappings();

    @Override
    @Nonnull
    public LegStatus convert(@Nullable T source) {
        if (source == null) {
            return LegStatus.UNKNOWN_LEG_STATUS;
        }
        LegStatus target = legStatusMappings.get(source.name());
        if (target == null) {
            LOGGER.warn("Exception: Failed to map unknown part result '{}' to LegStatus.", source);
            return LegStatus.UNKNOWN_LEG_STATUS;
        }
        return target;
    }

    private Map<String, LegStatus> getLegStatusMappings() {
        return new ImmutableMap.Builder<String, LegStatus>()
                .put("NONE", LegStatus.UNKNOWN_LEG_STATUS)
                .put("PLACED", LegStatus.PLACED)
                .put("VOIDED", LegStatus.VOID)
                .put("LOST", LegStatus.LOST)
                .put("WON", LegStatus.WON)
                .build();
    }
}