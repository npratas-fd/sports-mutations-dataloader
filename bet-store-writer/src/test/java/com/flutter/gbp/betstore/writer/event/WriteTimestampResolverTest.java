package com.flutter.gbp.betstore.writer.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.flutter.gbp.betstore.writer.config.WriterProperties.TimestampField;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class WriteTimestampResolverTest {

    private static final Map<String, String> CONTEXT = Map.of(
            "CREATION_TIME", "1785343197501",
            "generationClock", "1785343197635",
            "generationVersion", "4");

    @Test
    void creationTimeConvertedToMicros() {
        WriteTimestampResolver resolver = new WriteTimestampResolver(TimestampField.CREATION_TIME);

        assertThat(resolver.resolveMicros(CONTEXT)).isEqualTo(OptionalLong.of(1785343197501000L));
    }

    @Test
    void generationClockUsedWhenConfigured() {
        WriteTimestampResolver resolver = new WriteTimestampResolver(TimestampField.GENERATION_CLOCK);

        assertThat(resolver.resolveMicros(CONTEXT)).isEqualTo(OptionalLong.of(1785343197635000L));
    }

    @Test
    void missingOrNonNumericYieldsEmpty() {
        WriteTimestampResolver creationTime = new WriteTimestampResolver(TimestampField.CREATION_TIME);
        WriteTimestampResolver generationClock = new WriteTimestampResolver(TimestampField.GENERATION_CLOCK);
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("CREATION_TIME", null);

        assertThat(creationTime.resolveMicros(Map.of())).isEmpty();
        assertThat(creationTime.resolveMicros(null)).isEmpty();
        assertThat(creationTime.resolveMicros(nullValue)).isEmpty();
        assertThat(creationTime.resolveMicros(Map.of("CREATION_TIME", ""))).isEmpty();
        assertThat(creationTime.resolveMicros(Map.of("CREATION_TIME", "17853431975O1"))).isEmpty();
        assertThat(generationClock.resolveMicros(Map.of("CREATION_TIME", "1785343197501"))).isEmpty();
        assertThat(generationClock.resolveMicros(Map.of("generationClock", " "))).isEmpty();
    }
}
