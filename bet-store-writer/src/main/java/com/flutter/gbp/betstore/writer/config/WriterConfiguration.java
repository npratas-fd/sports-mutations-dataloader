package com.flutter.gbp.betstore.writer.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.flutter.gbp.betstore.cassandra.BetWriteRepository;
import com.flutter.gbp.betstore.writer.event.AbpEventAdapter;
import com.flutter.gbp.betstore.writer.event.BetEventHandler;
import com.flutter.gbp.betstore.writer.event.FbpEventAdapter;
import com.flutter.gbp.betstore.writer.event.WriteTimestampResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event handling beans of the writer: the Cassandra write repository on Boot's auto-configured {@link CqlSession},
 * the FBP / ABP adapters on the converters of {@code ConverterConfiguration} and the {@link BetEventHandler}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WriterProperties.class)
public class WriterConfiguration {

    @Bean
    BetWriteRepository betWriteRepository(CqlSession session) {
        return new BetWriteRepository(session);
    }

    @Bean
    WriteTimestampResolver writeTimestampResolver(WriterProperties properties) {
        return new WriteTimestampResolver(properties.timestampField());
    }

    @Bean
    FbpEventAdapter fbpEventAdapter(@Qualifier("fbpBetConverter")
                                    com.flutter.gbp.betstore.writer.convert.streams.fbp.bet.BetConverter fbpBetConverter) {
        return new FbpEventAdapter(fbpBetConverter);
    }

    @Bean
    AbpEventAdapter abpEventAdapter(@Qualifier("abpBetConverter")
                                    com.flutter.gbp.betstore.writer.convert.streams.abp.bet.BetConverter abpBetConverter) {
        return new AbpEventAdapter(abpBetConverter);
    }

    @Bean
    BetEventHandler betEventHandler(BetWriteRepository betWriteRepository, WriteTimestampResolver writeTimestampResolver,
                                    WriterProperties properties, MeterRegistry meterRegistry) {
        return new BetEventHandler(betWriteRepository, writeTimestampResolver,
                properties.toggles().obpBetsProcessing(), meterRegistry);
    }
}
