package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.abp.leg;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.leg.LegType;
import com.google.common.collect.ImmutableMap;
import com.ppb.platform.sb.retail.proto.bet.leg.LegOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class LegTypeConverter implements Converter<LegOuterClass.Leg.LegType, LegType> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegTypeConverter.class);
    private final Map<LegOuterClass.Leg.LegType, LegType> legTypeMappings = getLegTypeMappings();

    @Override
    @Nonnull
    public LegType convert(@Nullable LegOuterClass.Leg.LegType source) {
        if (source == null) {
            return LegType.UNKNOWN_LEG_TYPE;
        }
        LegType target = legTypeMappings.get(source);
        if (target == null) {
            LOGGER.warn("Exception: Failed to map unknown leg bet type '{}' to LegType.", source);
            return LegType.UNKNOWN_LEG_TYPE;
        }
        return target;
    }

    private Map<LegOuterClass.Leg.LegType, LegType> getLegTypeMappings() {
        return new ImmutableMap.Builder<LegOuterClass.Leg.LegType, LegType>()
                .put(LegOuterClass.Leg.LegType.UNKNOWN_LEG_TYPE, LegType.UNKNOWN_LEG_TYPE)
                .put(LegOuterClass.Leg.LegType.SIMPLE_SELECTION, LegType.SS)
                .put(LegOuterClass.Leg.LegType.FORECAST, LegType.SF)
                .put(LegOuterClass.Leg.LegType.REVERSE_FORECAST, LegType.RF)
                .put(LegOuterClass.Leg.LegType.COMBINATION_FORECAST, LegType.CF)
                .put(LegOuterClass.Leg.LegType.TRICAST, LegType.TC)
                .put(LegOuterClass.Leg.LegType.COMBINATION_TRICAST, LegType.CT)
                .build();
    }

}