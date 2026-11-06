package com.flutter.gbp.betstore.writer.convert.utils;

import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;

public class JavaValueToProtoValueConverter {
    public DoubleValue convertToDoubleValue(double value) {
        return DoubleValue.of(value);
    }

    public StringValue convertToStringValue(String value) {
        return StringValue.of(value);
    }

    public Timestamp convertToTimestamp(long millis) {
        return Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1_000_000))
                .build();
    }
}