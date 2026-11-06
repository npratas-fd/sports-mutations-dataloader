package com.flutter.gbp.betstore.writer.event;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.betstore.writer.config.WriterProperties.TimestampField;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Resolves the Cassandra write timestamp ({@code USING TIMESTAMP}) of a bet event from its context.
 */
public class WriteTimestampResolver {

    /** Context key of the event creation time (epoch millis); always the value of {@code bet_stream_timestamp}. */
    public static final String CREATION_TIME = TimestampField.CREATION_TIME.contextKey();

    private final TimestampField timestampField;

    /**
     * @param timestampField context field whose value (epoch millis) orders the writes of a bet
     */
    public WriteTimestampResolver(TimestampField timestampField) {
        checkArgument(timestampField != null, "timestampField must be provided");
        this.timestampField = timestampField;
    }

    /**
     * @param context event context
     * @return the configured field in microseconds (millis * 1000), or empty if it is missing, not numeric or too
     *         large to be expressed in microseconds
     */
    public OptionalLong resolveMicros(Map<String, String> context) {
        OptionalLong millis = readMillis(context, timestampField.contextKey());
        if (millis.isEmpty()) {
            return millis;
        }
        try {
            return OptionalLong.of(Math.multiplyExact(millis.getAsLong(), 1000L));
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * @param context event context (may be {@code null})
     * @param key     context key
     * @return the value of {@code key} as a long, or empty if it is missing or not numeric
     */
    static OptionalLong readMillis(Map<String, String> context, String key) {
        if (context == null) {
            return OptionalLong.empty();
        }
        String value = context.get(key);
        if (value == null || value.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
