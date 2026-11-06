package com.flutter.gbp.betstore.cassandra;

import static com.google.common.base.Preconditions.checkArgument;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.flutter.gbp.betstore.model.BetBlobCodec;
import com.flutter.gbp.betstore.model.BetStoreTables;
import java.util.Locale;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes bets to both {@code bets} and {@code bets_by_id} in a single logged batch.
 *
 * <p>Every statement carries an explicit write timestamp (microseconds), so an older write arriving after a newer
 * one (including an insert arriving after a delete) never wins. Calls are synchronous and propagate
 * {@link DriverException}.
 *
 * <p>Table names are not keyspace-qualified: they are resolved in the keyspace the session is bound to
 * ({@code spring.cassandra.keyspace-name}).
 *
 * <p>An upsert batch carries the bet blob twice and spans two partitions, so Cassandra rejects it when it exceeds
 * {@code batch_size_fail_threshold} (50 KiB by default) with an {@link InvalidQueryException} "Batch too large". The
 * upsert then falls back to running the two inserts one after the other, with the same write timestamps: each insert
 * is still idempotent and ordered by its timestamp, only the atomicity across the two tables is lost (if the second
 * insert fails, the caller retries the whole upsert).
 */
public class BetWriteRepository {

    private static final Logger LOG = LoggerFactory.getLogger(BetWriteRepository.class);

    /** Message of Cassandra's {@code InvalidRequestException} for a batch above {@code batch_size_fail_threshold}. */
    static final String BATCH_TOO_LARGE = "batch too large";

    /**
     * How an upsert reached Cassandra.
     */
    public enum WriteMode {
        /** Both tables in one logged batch (the normal case). */
        LOGGED_BATCH,
        /** The batch was too large; the two inserts were run separately with the same write timestamps. */
        UNBATCHED
    }

    private final CqlSession session;
    private final PreparedStatement insertByAccount;
    private final PreparedStatement insertById;
    private final PreparedStatement deleteByAccount;
    private final PreparedStatement deleteById;
    private final PreparedStatement deleteByAccountWithTimestamp;
    private final PreparedStatement deleteByIdWithTimestamp;

    /**
     * Creates the repository and prepares its statements.
     *
     * @param session session bound to the bet-store keyspace (tables are not keyspace-qualified), schema in place
     */
    public BetWriteRepository(CqlSession session) {
        checkArgument(session != null, "session must not be null");
        this.session = session;
        String bets = BetStoreTables.BETS;
        String betsById = BetStoreTables.BETS_BY_ID;
        this.insertByAccount = session.prepare("INSERT INTO " + bets
                + " (account_id, bet_id, bet_stream_timestamp, bet) VALUES (?, ?, ?, ?) USING TIMESTAMP ?");
        this.insertById = session.prepare("INSERT INTO " + betsById
                + " (bet_id, account_id, bet_stream_timestamp, bet) VALUES (?, ?, ?, ?) USING TIMESTAMP ?");
        this.deleteByAccount = session.prepare("DELETE FROM " + bets + " WHERE account_id = ? AND bet_id = ?");
        this.deleteById = session.prepare("DELETE FROM " + betsById + " WHERE bet_id = ?");
        this.deleteByAccountWithTimestamp =
                session.prepare("DELETE FROM " + bets + " USING TIMESTAMP ? WHERE account_id = ? AND bet_id = ?");
        this.deleteByIdWithTimestamp =
                session.prepare("DELETE FROM " + betsById + " USING TIMESTAMP ? WHERE bet_id = ?");
    }

    /**
     * Inserts or replaces the bet in both tables, using {@code record.writeTimestampMicros()} as write timestamp.
     * A batch rejected as too large is retried as two separate inserts (see the class documentation).
     *
     * @return {@link WriteMode#UNBATCHED} when the batch-too-large fallback was used, else {@link WriteMode#LOGGED_BATCH}
     * @throws DriverException if Cassandra rejects or fails the batch (other than for its size) or a fallback insert
     */
    public WriteMode upsert(BetRecord record) {
        checkArgument(record != null, "record must not be null");
        checkArgument(record.betId() != null && !record.betId().isEmpty(), "betId must not be empty");
        checkArgument(record.writeTimestampMicros() > 0, "writeTimestampMicros must be positive");
        checkArgument(record.streamTimestamp() != null, "streamTimestamp must not be null");
        checkArgument(record.bet() != null, "bet must not be null");

        var blob = BetBlobCodec.serialize(record.bet());
        BoundStatement byAccount = insertByAccount.bind(record.accountId(), record.betId(), record.streamTimestamp(),
                blob.duplicate(), record.writeTimestampMicros());
        BoundStatement byId = insertById.bind(record.betId(), record.accountId(), record.streamTimestamp(),
                blob.duplicate(), record.writeTimestampMicros());
        try {
            session.execute(BatchStatement.newInstance(BatchType.LOGGED, byAccount, byId));
        } catch (InvalidQueryException e) {
            if (!isBatchTooLarge(e)) {
                throw e;
            }
            LOG.warn("Batch too large for bet {} of account {} (blob {} bytes), writing the two tables separately "
                            + "with write timestamp {}", record.betId(), record.accountId(), blob.remaining(),
                    record.writeTimestampMicros());
            session.execute(byAccount);
            session.execute(byId);
            LOG.debug("Upserted bet {} for account {} at {} without batch", record.betId(), record.accountId(),
                    record.writeTimestampMicros());
            return WriteMode.UNBATCHED;
        }
        LOG.debug("Upserted bet {} for account {} at {}", record.betId(), record.accountId(), record.writeTimestampMicros());
        return WriteMode.LOGGED_BATCH;
    }

    /**
     * @return whether Cassandra rejected the batch for exceeding {@code batch_size_fail_threshold}
     */
    static boolean isBatchTooLarge(InvalidQueryException e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(BATCH_TOO_LARGE);
    }

    /**
     * Deletes the bet from both tables. With a timestamp the delete only shadows writes at or before it.
     *
     * <p>Without a timestamp the write timestamp is assigned on the client by the driver's timestamp generator
     * ({@code AtomicTimestampGenerator} by default): this host's clock in microseconds, not the Cassandra server's.
     * Upserts are timestamped with the producer's event time, so if the producer's clock is ahead of this host's, a
     * recent upsert of the bet carries a later timestamp than the delete and survives it.
     *
     * <p>The delete batch carries no blob, so unlike {@link #upsert} it cannot exceed the batch size threshold.
     *
     * @throws DriverException if Cassandra rejects or fails the batch
     */
    public void delete(long accountId, String betId, OptionalLong writeTimestampMicros) {
        checkArgument(betId != null && !betId.isEmpty(), "betId must not be empty");
        checkArgument(writeTimestampMicros != null, "writeTimestampMicros must not be null");
        checkArgument(writeTimestampMicros.isEmpty() || writeTimestampMicros.getAsLong() > 0,
                "writeTimestampMicros must be positive");

        BatchStatement batch;
        if (writeTimestampMicros.isPresent()) {
            long ts = writeTimestampMicros.getAsLong();
            batch = BatchStatement.newInstance(BatchType.LOGGED,
                    deleteByAccountWithTimestamp.bind(ts, accountId, betId),
                    deleteByIdWithTimestamp.bind(ts, betId));
        } else {
            batch = BatchStatement.newInstance(BatchType.LOGGED,
                    deleteByAccount.bind(accountId, betId),
                    deleteById.bind(betId));
        }
        session.execute(batch);
        LOG.debug("Deleted bet {} for account {}", betId, accountId);
    }
}
