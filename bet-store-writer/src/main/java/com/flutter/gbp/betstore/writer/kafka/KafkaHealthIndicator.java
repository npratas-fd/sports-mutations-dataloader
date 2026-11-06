package com.flutter.gbp.betstore.writer.kafka;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Health of the writer's Kafka side, reported as the {@code kafka} component of {@code /actuator/health}.
 *
 * <p>Boot 4.1 has no Kafka health indicator. The writer is {@code UP} when every listener container that is enabled
 * ({@code bet-store.consumers.*.enabled}) is running and the broker answers a {@code describeCluster} request within
 * a few seconds. A listener container stops by itself on a fatal consumer error, which is what the first check
 * catches; the second one catches an unreachable cluster, which a running container alone would not show.
 */
public class KafkaHealthIndicator extends AbstractHealthIndicator implements AutoCloseable {

    static final int BROKER_TIMEOUT_MS = 3_000;

    /** Asks the cluster for its id; throws if it does not answer in time. */
    @FunctionalInterface
    interface ClusterProbe {
        String clusterId() throws Exception;
    }

    private final Supplier<Collection<MessageListenerContainer>> containers;
    private final ClusterProbe clusterProbe;
    private final AutoCloseable resource;

    /**
     * @param registry   registry of the listener containers
     * @param kafkaAdmin Boot's admin configuration, used to reach the same cluster as the consumers
     */
    public KafkaHealthIndicator(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin) {
        this(registry::getListenerContainers, new AdminClusterProbe(kafkaAdmin.getConfigurationProperties()));
    }

    KafkaHealthIndicator(Supplier<Collection<MessageListenerContainer>> containers, ClusterProbe clusterProbe) {
        super("Kafka health check failed");
        checkArgument(containers != null, "containers must be provided");
        checkArgument(clusterProbe != null, "clusterProbe must be provided");
        this.containers = containers;
        this.clusterProbe = clusterProbe;
        this.resource = clusterProbe instanceof AutoCloseable closeable ? closeable : () -> { };
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        List<String> stopped = containers.get().stream()
                .filter(MessageListenerContainer::isAutoStartup)
                .filter(container -> !container.isRunning())
                .map(MessageListenerContainer::getListenerId)
                .sorted()
                .toList();
        if (!stopped.isEmpty()) {
            builder.down().withDetail("stoppedListeners", stopped);
            return;
        }
        builder.up().withDetail("clusterId", clusterProbe.clusterId());
    }

    @Override
    public void close() throws Exception {
        resource.close();
    }

    /** Lazily created admin client; it connects on demand, so an unreachable broker shows up as a timeout. */
    static final class AdminClusterProbe implements ClusterProbe, AutoCloseable {

        private final Map<String, Object> adminProperties;
        private Admin admin;

        AdminClusterProbe(Map<String, Object> adminConfiguration) {
            this.adminProperties = new java.util.HashMap<>(adminConfiguration);
            adminProperties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, BROKER_TIMEOUT_MS);
            adminProperties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, BROKER_TIMEOUT_MS);
        }

        @Override
        public synchronized String clusterId() throws Exception {
            if (admin == null) {
                admin = Admin.create(adminProperties);
            }
            return admin.describeCluster().clusterId().get(BROKER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void close() {
            if (admin != null) {
                admin.close(java.time.Duration.ofSeconds(1));
                admin = null;
            }
        }
    }
}
