package com.flutter.gbp.betstore.reader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * bet-store reader: serves the open bets stored by the writer over HTTP (protobuf or JSON).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BetStoreReaderApplication {

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BetStoreReaderApplication.class, args);
    }
}
