package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp;

import com.flutter.gbp.sportsmutationsdataloader.writer.convert.Converter;
import com.flutter.gbp.fcq.bet.PriceType;
import com.google.common.collect.ImmutableMap;
import com.ppb.platform.sb.fbp.proto.bet.part.FractionalPriceOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class PriceTypeConverter implements Converter<FractionalPriceOuterClass.FractionalPrice.PriceType, PriceType> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceTypeConverter.class);
    private final Map<FractionalPriceOuterClass.FractionalPrice.PriceType, PriceType> priceTypeMappings = getPriceTypeMappings();

    @Override
    @Nonnull
    public PriceType convert(@Nullable FractionalPriceOuterClass.FractionalPrice.PriceType source) {
        if (source == null) {
            return PriceType.UNKNOWN_PRICE_TYPE;
        }
        PriceType target = priceTypeMappings.get(source);
        if (target == null) {
            LOGGER.warn("Exception: Failed to map unknown fractional price type '{}' to PriceType.", source);
            return PriceType.UNKNOWN_PRICE_TYPE;
        }
        return target;
    }

    private Map<FractionalPriceOuterClass.FractionalPrice.PriceType, PriceType> getPriceTypeMappings() {
        return new ImmutableMap.Builder<FractionalPriceOuterClass.FractionalPrice.PriceType, PriceType>()
                .put(FractionalPriceOuterClass.FractionalPrice.PriceType.LIVE, PriceType.LIVE)
                .put(FractionalPriceOuterClass.FractionalPrice.PriceType.GUARANTEED, PriceType.GUARANTEED)
                .put(FractionalPriceOuterClass.FractionalPrice.PriceType.STARTING, PriceType.STARTING)
                .put(FractionalPriceOuterClass.FractionalPrice.PriceType.DIVIDEND, PriceType.DIVIDEND)
                .build();
    }

}