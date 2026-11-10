package com.flutter.gbp.sportsmutationsdataloader.writer.kafka;

import com.ppb.platform.sb.fbp.proto.event.BetDomainEventOuterClass;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Deserializer for FBP Kafka messages.
 */
public class FbpDeserializer implements Deserializer<BetDomainEventOuterClass.BetDomainEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FbpDeserializer.class);

    /**
     * Deserializer method for FBP Kafka messages.
     *
     * @param topic - a {@link String} containing the Kafka topic name.
     * @param data  - a byte array containing the message data.
     * @return {@link BetDomainEventOuterClass.BetDomainEvent} object.
     */
    @Override
    public BetDomainEventOuterClass.BetDomainEvent deserialize(String topic, byte[] data) {
        try {
            return BetDomainEventOuterClass.BetDomainEvent.parseDelimitedFrom(new ByteArrayInputStream(data));
        } catch (IOException ioException) {
            LOGGER.error("operation=deserialize, msg='Exception occurred when deserializing data from FBP'");
            throw new RuntimeException(ioException);
        }
    }

}
