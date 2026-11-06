package com.flutter.gbp.betstore.cassandra;

import static com.google.common.base.Preconditions.checkArgument;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.flutter.gbp.betstore.model.BetBlobCodec;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes bets to both {@code bets} and {@code bets_by_id} in a single logged batch.
 *
 * <p>Every statement carries an explicit write timestamp (microseconds), so an older write arriving after a newer
 * one (including an insert arriving after a delete) never wins. Calls are synchronous and propagate
 * {@link DriverException}.
 */
public class BetWriteRepository {

    private static final Logger LOG = LoggerFactory.getLogger(BetWriteRepository.class);

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
     * @param session session whose keyspace schema is already in place
     */
    public BetWriteRepository(CqlSession session) {
        checkArgument(session != null, "session must not be null");
        this.session = session;
        this.insertByAccount = session.prepare(
                "INSERT INTO bet_store.bets (account_id, bet_id, bet_stream_timestamp, bet) VALUES (?, ?, ?, ?) USING TIMESTAMP ?");
        this.insertById = session.prepare(
                "INSERT INTO bet_store.bets_by_id (bet_id, account_id, bet_stream_timestamp, bet) VALUES (?, ?, ?, ?) USING TIMESTAMP ?");
        this.deleteByAccount = session.prepare("DELETE FROM bet_store.bets WHERE account_id = ? AND bet_id = ?");
        this.deleteById = session.prepare("DELETE FROM bet_store.bets_by_id WHERE bet_id = ?");
        this.deleteByAccountWithTimestamp =
                session.prepare("DELETE FROM bet_store.bets USING TIMESTAMP ? WHERE account_id = ? AND bet_id = ?");
        this.deleteByIdWithTimestamp =
                session.prepare("DELETE FROM bet_store.bets_by_id USING TIMESTAMP ? WHERE bet_id = ?");
    }

    /**
     * Inserts or replaces the bet in both tables, using {@code record.writeTimestampMicros()} as write timestamp.
     *
     * @throws DriverException if Cassandra rejects or fails the batch
     */
    public void upsert(BetRecord record) {
        checkArgument(record != null, "record must not be null");
        checkArgument(record.betId() != null && !record.betId().isEmpty(), "betId must not be empty");
        checkArgument(record.writeTimestampMicros() > 0, "writeTimestampMicros must be positive");
        checkArgument(record.streamTimestamp() != null, "streamTimestamp must not be null");
        checkArgument(record.bet() != null, "bet must not be null");

        var blob = BetBlobCodec.serialize(record.bet());
        BatchStatement batch = BatchStatement.newInstance(BatchType.LOGGED,
                insertByAccount.bind(record.accountId(), record.betId(), record.streamTimestamp(), blob.duplicate(),
                        record.writeTimestampMicros()),
                insertById.bind(record.betId(), record.accountId(), record.streamTimestamp(), blob.duplicate(),
                        record.writeTimestampMicros()));
        session.execute(batch);
        LOG.debug("Upserted bet {} for account {} at {}", record.betId(), record.accountId(), record.writeTimestampMicros());
    }

    /**
     * Deletes the bet from both tables. With a timestamp the delete only shadows writes at or before it; without one
     * Cassandra assigns the current time.
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
