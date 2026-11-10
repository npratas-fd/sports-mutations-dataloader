package com.flutter.gbp.sportsmutationsdataloader.writer.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.listener.MessageListenerContainer;

class KafkaHealthIndicatorTest {

    @Test
    void upWhenEnabledListenersRunAndBrokerAnswers() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(
                () -> List.of(container("fbpBetEvents", true, true), container("abpBetEvents", true, true)),
                () -> "cluster-1");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterId", "cluster-1");
    }

    @Test
    void disabledListenerIsNotRequiredToRun() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(
                () -> List.of(container("fbpBetEvents", true, true), container("abpBetEvents", false, false)),
                () -> "cluster-1");

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenAnEnabledListenerStopped() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(
                () -> List.of(container("fbpBetEvents", true, true), container("abpBetEvents", true, false)),
                () -> "cluster-1");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("stoppedListeners", List.of("abpBetEvents"));
    }

    @Test
    void downWhenBrokerDoesNotAnswer() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(
                () -> List.of(container("fbpBetEvents", true, true)),
                () -> {
                    throw new TimeoutException("describeCluster timed out");
                });

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    private static MessageListenerContainer container(String id, boolean autoStartup, boolean running) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getListenerId()).thenReturn(id);
        when(container.isAutoStartup()).thenReturn(autoStartup);
        when(container.isRunning()).thenReturn(running);
        return container;
    }
}
