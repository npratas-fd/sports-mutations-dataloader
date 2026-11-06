package com.flutter.gbp.betstore.writer.convert.streams.abp.bet;

import com.flutter.gbp.betstore.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.BetStatus;
import com.google.common.collect.ImmutableMap;
import com.ppb.platform.sb.retail.proto.bet.status.BetStatusOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class BetStatusConverter implements Converter<BetStatusOuterClass.BetStatus, BetStatus> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetStatusConverter.class);
    private final Map<BetStatusOuterClass.BetStatus, BetStatus> betStatusMappings = getBetStatusMappings();

    @Override
    @Nonnull
    public BetStatus convert(@Nullable BetStatusOuterClass.BetStatus source) {
        if (source == null) {
            return BetStatus.UNKNOWN_BET_STATUS;
        }
        BetStatus target = betStatusMappings.get(source);
        if (target == null) {
            LOGGER.warn("Exception: Failed to map unknown bet status '{}' to BetStatus.", source);
            return BetStatus.UNKNOWN_BET_STATUS;
        }
        return target;
    }

    private Map<BetStatusOuterClass.BetStatus, BetStatus> getBetStatusMappings() {
        return new ImmutableMap.Builder<BetStatusOuterClass.BetStatus, BetStatus>()
                .put(BetStatusOuterClass.BetStatus.UNKNOWN_BET_STATUS, BetStatus.UNKNOWN_BET_STATUS)
                .put(BetStatusOuterClass.BetStatus.ACTIVE, BetStatus.ACTIVE)
                .put(BetStatusOuterClass.BetStatus.SCANNED, BetStatus.ACTIVE)
                .put(BetStatusOuterClass.BetStatus.CANCELLED, BetStatus.CANCELLED)
                .put(BetStatusOuterClass.BetStatus.CASHED_OUT, BetStatus.CASHED_OUT)
                .build();
    }

}