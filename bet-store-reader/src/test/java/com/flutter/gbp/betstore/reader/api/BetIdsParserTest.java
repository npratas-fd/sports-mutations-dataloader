package com.flutter.gbp.betstore.reader.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BetIdsParserTest {

    @Test
    void normalizesInput() {
        assertThat(BetIdsParser.parse("1,,2, 2 ")).containsExactly("1", "2");
    }

    @Test
    void blankYieldsEmpty() {
        assertThat(BetIdsParser.parse(" , ")).isEqualTo(Set.of());
    }

    @Test
    void nullYieldsEmpty() {
        assertThat(BetIdsParser.parse(null)).isEmpty();
    }
}
