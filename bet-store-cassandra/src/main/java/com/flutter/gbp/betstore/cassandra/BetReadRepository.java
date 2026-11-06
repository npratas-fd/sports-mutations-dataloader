package com.flutter.gbp.betstore.cassandra;

import static com.google.common.base.Preconditions.checkArgument;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.flutter.gbp.betstore.model.BetBlobCodec;
import com.flutter.gbp.betstore.model.BetStoreTables;
import com.flutter.gbp.fcq.bet.Bet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads bets by account, by account and bet ids, or by bet ids alone.
 *
 * <p>Account queries are ordered by ascending {@code bet_id} and capped at the configured bet limit (embedded in the
 * prepared statement). All pages of every result set are read. Missing bets are simply absent from the result and
 * blobs that cannot be deserialized are skipped. {@link DriverException} propagates to the caller.
 */
public class BetReadRepository {

    private static final Logger LOG = LoggerFactory.getLogger(BetReadRepository.class);

    private static final String BETS = BetStoreTables.KEYSPACE + "." + BetStoreTables.BETS;
    private static final String BETS_BY_ID = BetStoreTables.KEYSPACE + "." + BetStoreTables.BETS_BY_ID;

    private final CqlSession session;
    private final PreparedStatement selectByAccount;
    private final PreparedStatement selectByAccountAndBetIds;
    private final PreparedStatement selectById;

    /**
     * Creates the repository and prepares its statements.
     *
     * @param session  session whose keyspace schema is already in place
     * @param betLimit maximum number of bets returned by an account query; must be positive
     */
    public BetReadRepository(CqlSession session, int betLimit) {
        checkArgument(session != null, "session must not be null");
        checkArgument(betLimit > 0, "betLimit must be positive");
        this.session = session;
        String select = "SELECT " + BetStoreTables.BET + " FROM " + BETS + " WHERE " + BetStoreTables.ACCOUNT_ID + " = ?";
        String orderLimit = " ORDER BY " + BetStoreTables.BET_ID + " ASC LIMIT " + betLimit;
        this.selectByAccount = session.prepare(select + orderLimit);
        this.selectByAccountAndBetIds =
                session.prepare(select + " AND " + BetStoreTables.BET_ID + " IN :betIds" + orderLimit);
        this.selectById = session.prepare("SELECT " + BetStoreTables.BET_ID + ", " + BetStoreTables.BET + " FROM "
                + BETS_BY_ID + " WHERE " + BetStoreTables.BET_ID + " = ?");
    }

    /**
     * Returns the account's bets ordered by ascending bet id, up to the bet limit.
     *
     * @throws DriverException if Cassandra fails the query
     */
    public List<Bet> findByAccount(long accountId) {
        List<Bet> bets = new ArrayList<>();
        for (Row row : session.execute(selectByAccount.bind(accountId))) {
            decode(row).ifPresent(bets::add);
        }
        return bets;
    }

    /**
     * Returns the requested bets that belong to the account, ordered by ascending bet id, up to the bet limit.
     *
     * @throws DriverException if Cassandra fails the query
     */
    public List<Bet> findByAccountAndBetIds(long accountId, Set<String> betIds) {
        checkArgument(betIds != null, "betIds must not be null");
        if (betIds.isEmpty()) {
            return List.of();
        }
        List<Bet> bets = new ArrayList<>();
        for (Row row : session.execute(selectByAccountAndBetIds.bind(accountId, new ArrayList<>(betIds)))) {
            decode(row).ifPresent(bets::add);
        }
        return bets;
    }

    /**
     * Returns the requested bets from any account, ordered by ascending bet id. One keyed query per id runs in
     * parallel; an empty input returns an empty list without querying.
     *
     * @throws DriverException if Cassandra fails any of the queries
     */
    public List<Bet> findByBetIds(Set<String> betIds) {
        checkArgument(betIds != null, "betIds must not be null");
        if (betIds.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<AsyncResultSet>> futures = betIds.stream()
                .map(id -> session.executeAsync(selectById.bind(id)).toCompletableFuture())
                .toList();
        Map<String, Bet> byId = new TreeMap<>();
        for (CompletableFuture<AsyncResultSet> future : futures) {
            AsyncResultSet page = join(future);
            while (true) {
                for (Row row : page.currentPage()) {
                    decode(row).ifPresent(bet -> byId.put(row.getString(BetStoreTables.BET_ID), bet));
                }
                if (!page.hasMorePages()) {
                    break;
                }
                page = join(page.fetchNextPage().toCompletableFuture());
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof DriverException driverException) {
                throw driverException;
            }
            throw e;
        }
    }

    private static Optional<Bet> decode(Row row) {
        Optional<Bet> bet = BetBlobCodec.deserialize(row.getByteBuffer(BetStoreTables.BET));
        if (bet.isEmpty()) {
            LOG.warn("Skipping bet row with undeserializable blob");
        }
        return bet;
    }
}
