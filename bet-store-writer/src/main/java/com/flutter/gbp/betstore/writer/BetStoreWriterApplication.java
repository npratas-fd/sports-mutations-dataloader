package com.flutter.gbp.betstore.writer;

import com.flutter.gbp.betstore.writer.config.WriterConfiguration;
import com.flutter.gbp.betstore.writer.convert.ConverterConfiguration;
import com.flutter.gbp.betstore.writer.kafka.KafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * bet-store writer: consumes the FBP and ABP bet topics and keeps the open bets in Cassandra.
 *
 * <p>No component scanning: the MapStruct mappers copied from FCQ are generated as {@code @Component}s with clashing
 * default names (FBP and ABP share simple names). They are registered by {@link ConverterConfiguration} under the
 * FCQ bean names instead, so every configuration is imported explicitly: converters, event handling
 * ({@link WriterConfiguration}) and Kafka consumers ({@link KafkaConfiguration}).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ConverterConfiguration.class, WriterConfiguration.class, KafkaConfiguration.class})
public class BetStoreWriterApplication {

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BetStoreWriterApplication.class, args);
    }
}
