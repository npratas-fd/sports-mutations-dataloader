package com.flutter.gbp.betstore.reader.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.flutter.gbp.betstore.cassandra.BetReadRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Read repository on Boot's auto-configured {@link CqlSession}, and protobuf HTTP support.
 */
@Configuration(proxyBeanMethods = false)
public class ReaderConfiguration {

    @Bean
    BetReadRepository betReadRepository(CqlSession session, ReaderProperties properties) {
        return new BetReadRepository(session, properties.betLimit());
    }

    /**
     * Puts the protobuf converter first so protobuf messages are never serialized by Jackson and the default
     * representation (no Accept header, or {@code *}/{@code *}) is {@code application/x-protobuf}.
     */
    @Configuration(proxyBeanMethods = false)
    static class ProtobufWebConfiguration implements WebMvcConfigurer {

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(0, new ProtobufHttpMessageConverter());
        }
    }
}
