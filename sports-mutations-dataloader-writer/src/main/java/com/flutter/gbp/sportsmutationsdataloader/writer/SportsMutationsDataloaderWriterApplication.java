package com.flutter.gbp.sportsmutationsdataloader.writer;

import com.flutter.gbp.sportsmutationsdataloader.writer.config.WriterConfiguration;
import com.flutter.gbp.sportsmutationsdataloader.writer.convert.ConverterConfiguration;
import com.flutter.gbp.sportsmutationsdataloader.writer.kafka.KafkaConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * sports-mutations-dataloader writer: consumes the FBP and ABP bet topics and keeps the open bets in Cassandra.
 *
 * <p>No component scanning: the MapStruct mappers copied from FCQ are generated as {@code @Component}s with clashing
 * default names (FBP and ABP share simple names). They are registered by {@link ConverterConfiguration} under the
 * FCQ bean names instead, so every configuration is imported explicitly: converters, event handling
 * ({@link WriterConfiguration}) and Kafka consumers ({@link KafkaConfiguration}).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ConverterConfiguration.class, WriterConfiguration.class, KafkaConfiguration.class})
public class SportsMutationsDataloaderWriterApplication {

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SportsMutationsDataloaderWriterApplication.class, args);
    }
}
