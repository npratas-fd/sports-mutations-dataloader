package com.flutter.gbp.sportsmutationsdataloader.model;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.fcq.bet.Bet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a {@link Bet} to and from the protobuf blob stored in Cassandra.
 */
public final class BetBlobCodec {

    private static final Logger LOG = LoggerFactory.getLogger(BetBlobCodec.class);

    private BetBlobCodec() {
    }

    /**
     * Serializes a bet into a read-only buffer ready to be bound to a Cassandra blob column.
     *
     * @param bet the bet to serialize, never null
     * @return the protobuf bytes of the bet
     */
    public static ByteBuffer serialize(Bet bet) {
        checkArgument(bet != null, "bet must not be null");
        return ByteBuffer.wrap(bet.toByteArray());
    }

    /**
     * Parses a bet from a blob. The given buffer's position is left untouched.
     *
     * @param bytes the stored bytes, may be null
     * @return the bet, or empty when the bytes are null or not a valid {@link Bet}
     */
    public static Optional<Bet> deserialize(ByteBuffer bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Bet.parseFrom(bytes.duplicate()));
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Unable to parse stored bet blob of {} bytes", bytes.remaining(), e);
            return Optional.empty();
        }
    }
}
