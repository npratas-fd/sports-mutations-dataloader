package com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils;

import com.google.protobuf.DoubleValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;

public class JavaValueToProtoValueConverterTest {

    private JavaValueToProtoValueConverter victim;

    @BeforeMethod
    public void setUp() {
        victim = new JavaValueToProtoValueConverter();
    }

    @Test
    public void testToDoubleValue() {
        double value = 42.5;
        DoubleValue expected = DoubleValue.of(value);
        DoubleValue actual = victim.convertToDoubleValue(value);
        assertEquals(actual, expected);
    }

    @Test
    public void testToStringValue() {
        String value = "Hello, World!";
        StringValue expected = StringValue.of(value);
        StringValue actual = victim.convertToStringValue(value);
        assertEquals(actual, expected);
    }

    @Test
    public void testToTimestamp() {
        ZonedDateTime dateTime = ZonedDateTime.of(2023, 4, 15, 15, 30, 0, 0, ZoneId.of("UTC+1"));
        Instant instant = dateTime.toInstant();

        // Create a Timestamp from the Instant
        Timestamp expectedTimestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();

        long millis = 1_681_569_000_000L; // Corresponds to 15/04/2023 @ 15:30:00 UTC+1
        Timestamp actual = victim.convertToTimestamp(millis);
        assertEquals(actual, expectedTimestamp);
    }
}