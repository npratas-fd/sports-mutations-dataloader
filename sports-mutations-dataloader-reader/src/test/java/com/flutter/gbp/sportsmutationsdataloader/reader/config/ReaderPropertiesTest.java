package com.flutter.gbp.sportsmutationsdataloader.reader.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReaderPropertiesTest {

    @Test
    void positiveValuesAreAccepted() {
        ReaderProperties properties = new ReaderProperties(70, 70, 1000);

        assertThat(properties.betLimit()).isEqualTo(70);
        assertThat(properties.maxBetIds()).isEqualTo(70);
        assertThat(properties.maxAccountBetIds()).isEqualTo(1000);
    }

    @Test
    void nonPositiveValuesAreRejected() {
        assertThatThrownBy(() -> new ReaderProperties(0, 70, 1000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("betLimit");
        assertThatThrownBy(() -> new ReaderProperties(70, -1, 1000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxBetIds");
        assertThatThrownBy(() -> new ReaderProperties(70, 70, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAccountBetIds");
    }
}
