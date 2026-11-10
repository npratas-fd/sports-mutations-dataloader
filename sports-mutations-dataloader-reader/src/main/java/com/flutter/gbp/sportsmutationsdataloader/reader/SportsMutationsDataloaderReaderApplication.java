package com.flutter.gbp.sportsmutationsdataloader.reader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * sports-mutations-dataloader reader: serves the open bets stored by the writer over HTTP (protobuf or JSON).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SportsMutationsDataloaderReaderApplication {

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SportsMutationsDataloaderReaderApplication.class, args);
    }
}
