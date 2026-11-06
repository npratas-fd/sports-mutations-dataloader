package com.flutter.gbp.betstore.writer.kafka;

import com.datastax.oss.driver.api.core.DriverException;
import com.flutter.gbp.betstore.writer.event.AbpEventAdapter;
import com.flutter.gbp.betstore.writer.event.BetEventHandler;
import com.flutter.gbp.betstore.writer.event.FbpEventAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer wiring of the writer.
 *
 * <p>One listener container factory per topic, because FBP and ABP use different value deserializers. Both start from
 * Boot's {@code spring.kafka.consumer.*} properties and are configured by Boot's
 * {@link ConcurrentKafkaListenerContainerFactoryConfigurer}, which applies {@code spring.kafka.listener.*}
 * ({@code ack-mode=record}, {@code concurrency}) and the {@link DefaultErrorHandler} bean below.
 *
 * <p>Error handling:
 * <ul>
 *     <li>a Cassandra failure ({@link DriverException} anywhere in the cause chain) is retried forever with
 *     exponential backoff ({@value #CASSANDRA_INITIAL_INTERVAL_MS} ms up to {@value #CASSANDRA_MAX_INTERVAL_MS} ms).
 *     The record is re-sought and its offset is not committed, so nothing is lost and the partition keeps its order;
 *     the container is never stopped;</li>
 *     <li>any other failure, including a record that cannot be deserialized ({@link ErrorHandlingDeserializer}), is
 *     logged, counted and skipped: the offset is committed and the partition moves on.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConfiguration {

    /** Container factory of the FBP listener. */
    public static final String FBP_CONTAINER_FACTORY = "fbpKafkaListenerContainerFactory";
    /** Container factory of the ABP listener. */
    public static final String ABP_CONTAINER_FACTORY = "abpKafkaListenerContainerFactory";

    static final long CASSANDRA_INITIAL_INTERVAL_MS = 500L;
    static final long CASSANDRA_MAX_INTERVAL_MS = 30_000L;
    static final double CASSANDRA_BACKOFF_MULTIPLIER = 2.0;

    static final String SKIPPED_RECORDS_METRIC = "betstore.writer.records.skipped";

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfiguration.class);

    @Bean(FBP_CONTAINER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass.BetDomainEvent>
            fbpKafkaListenerContainerFactory(ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
                                             KafkaProperties kafkaProperties) {
        return containerFactory(configurer, kafkaProperties, FbpDeserializer::new);
    }

    @Bean(ABP_CONTAINER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, com.ppb.platform.sb.retail.proto.event.BetDomainEventOuterClass.BetDomainEvent>
            abpKafkaListenerContainerFactory(ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
                                             KafkaProperties kafkaProperties) {
        return containerFactory(configurer, kafkaProperties, AbpDeserializer::new);
    }

    @Bean
    BetEventListeners betEventListeners(FbpEventAdapter fbpEventAdapter, AbpEventAdapter abpEventAdapter,
                                        BetEventHandler betEventHandler) {
        return new BetEventListeners(fbpEventAdapter, abpEventAdapter, betEventHandler);
    }

    /**
     * Picked up by Boot's container factory configurer and therefore applied to both factories.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(MeterRegistry meterRegistry) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> logAndSkip(record, exception, meterRegistry), new FixedBackOff(0L, 0L));
        errorHandler.setBackOffFunction((record, exception) -> isCassandraFailure(exception) ? cassandraBackOff() : null);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (isCassandraFailure(exception)) {
                LOGGER.warn("Cassandra write failed, retrying without committing the offset, topic: {}, partition: {}, "
                                + "offset: {}, key: {}, attempt: {}, error: {}", record.topic(), record.partition(),
                        record.offset(), record.key(), deliveryAttempt, rootCause(exception).toString());
            }
        });
        return errorHandler;
    }

    /**
     * @return unlimited exponential backoff for Cassandra failures
     */
    static BackOff cassandraBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(CASSANDRA_INITIAL_INTERVAL_MS, CASSANDRA_BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(CASSANDRA_MAX_INTERVAL_MS);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        backOff.setMaxAttempts(Long.MAX_VALUE);
        return backOff;
    }

    /**
     * @param exception failure of a listener invocation
     * @return whether a Cassandra driver failure is part of its cause chain
     */
    static boolean isCassandraFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DriverException) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <V> ConcurrentKafkaListenerContainerFactory<String, V> containerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties kafkaProperties,
            Supplier<Deserializer<V>> valueDeserializer) {
        ConsumerFactory<String, V> consumerFactory = new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                StringDeserializer::new,
                () -> new ErrorHandlingDeserializer<>(valueDeserializer.get()));
        ConcurrentKafkaListenerContainerFactory<String, V> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure((ConcurrentKafkaListenerContainerFactory) factory, (ConsumerFactory) consumerFactory);
        return factory;
    }

    private static void logAndSkip(ConsumerRecord<?, ?> record, Exception exception, MeterRegistry meterRegistry) {
        LOGGER.error("Skipped record that could not be processed, topic: {}, partition: {}, offset: {}, key: {}",
                record.topic(), record.partition(), record.offset(), record.key(), exception);
        Counter.builder(SKIPPED_RECORDS_METRIC)
                .tag("topic", record.topic())
                .tag("exception", rootCause(exception).getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
