package com.flutter.gbp.sportsmutationsdataloader.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.flutter.gbp.sportsmutationsdataloader.model.BetBlobCodec;
import com.flutter.gbp.fcq.bet.Bet;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BetWriteRepositoryIT {

    private static final long ACCOUNT_ID = 42L;
    private static final String BET_ID = "bet-1";
    private static final String OTHER_KEYSPACE = "sports_mutations_dataloader_other";

    private final CqlSession session = CassandraTestSupport.session();
    private final BetWriteRepository repository = new BetWriteRepository(session);

    @BeforeEach
    void cleanTables() {
        CassandraTestSupport.truncateAll();
    }

    @Test
    void upsertWritesBothTables() {
        Instant streamTimestamp = Instant.ofEpochMilli(1_700_000_000_000L);
        Bet bet = bet("A");

        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, streamTimestamp, bet, 1000L));

        List<Row> byAccount = rows("SELECT * FROM sports_mutations_dataloader.bets WHERE account_id = ? AND bet_id = ?", ACCOUNT_ID, BET_ID);
        List<Row> byId = rows("SELECT * FROM sports_mutations_dataloader.bets_by_id WHERE bet_id = ?", BET_ID);
        assertThat(byAccount).hasSize(1);
        assertThat(byId).hasSize(1);
        for (Row row : List.of(byAccount.get(0), byId.get(0))) {
            assertThat(row.getLong("account_id")).isEqualTo(ACCOUNT_ID);
            assertThat(row.getInstant("bet_stream_timestamp")).isEqualTo(streamTimestamp);
            assertThat(BetBlobCodec.deserialize(row.getByteBuffer("bet"))).contains(bet);
        }
    }

    @Test
    void olderUpsertDoesNotOverwriteNewer() {
        Bet a = bet("A");
        Bet b = bet("B");

        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(2), a, 2000L));
        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), b, 1000L));

        assertThat(storedBet("SELECT bet FROM sports_mutations_dataloader.bets WHERE account_id = ? AND bet_id = ?", ACCOUNT_ID, BET_ID))
                .contains(a);
        assertThat(storedBet("SELECT bet FROM sports_mutations_dataloader.bets_by_id WHERE bet_id = ?", BET_ID)).contains(a);
    }

    @Test
    void deleteRemovesFromBothTables() {
        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), bet("A"), 1000L));

        repository.delete(ACCOUNT_ID, BET_ID, OptionalLong.of(2000L));

        assertNoRows();
    }

    @Test
    void lateUpsertAfterDeleteStaysDeleted() {
        repository.delete(ACCOUNT_ID, BET_ID, OptionalLong.of(2000L));

        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), bet("A"), 1000L));

        assertNoRows();
    }

    @Test
    void newerUpsertAfterOlderDeleteSurvives() {
        Bet a = bet("A");
        repository.delete(ACCOUNT_ID, BET_ID, OptionalLong.of(1000L));

        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(2), a, 2000L));

        assertThat(storedBet("SELECT bet FROM sports_mutations_dataloader.bets WHERE account_id = ? AND bet_id = ?", ACCOUNT_ID, BET_ID))
                .contains(a);
        assertThat(storedBet("SELECT bet FROM sports_mutations_dataloader.bets_by_id WHERE bet_id = ?", BET_ID)).contains(a);
    }

    @Test
    void deleteWithoutTimestampRemoves() {
        repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), bet("A"), 1000L));

        repository.delete(ACCOUNT_ID, BET_ID, OptionalLong.empty());

        assertNoRows();
    }

    /**
     * The blob is written twice in the batch, so ~30 KB of bet exceeds Cassandra's default
     * {@code batch_size_fail_threshold} (50 KiB) and the batch is rejected with "Batch too large".
     */
    @Test
    void oversizedBatchFallsBackToSeparateInsertsAndKeepsTimestampOrder() {
        Bet large = largeBet("L", 30_000);
        Bet older = largeBet("O", 30_000);

        BetWriteRepository.WriteMode mode =
                repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(2), large, 2000L));
        BetWriteRepository.WriteMode olderMode =
                repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), older, 1000L));

        assertThat(mode).isEqualTo(BetWriteRepository.WriteMode.UNBATCHED);
        assertThat(olderMode).isEqualTo(BetWriteRepository.WriteMode.UNBATCHED);
        assertThat(storedBet("SELECT bet FROM sports_mutations_dataloader.bets WHERE account_id = ? AND bet_id = ?", ACCOUNT_ID, BET_ID))
                .contains(large);
        assertThat(storedBet("SELECT bet FROM sports_mutations_dataloader.bets_by_id WHERE bet_id = ?", BET_ID)).contains(large);

        repository.delete(ACCOUNT_ID, BET_ID, OptionalLong.of(3000L));

        assertNoRows();
    }

    @Test
    void smallBetIsWrittenInALoggedBatch() {
        assertThat(repository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), bet("A"), 1000L)))
                .isEqualTo(BetWriteRepository.WriteMode.LOGGED_BATCH);
    }

    @Test
    void writesGoToTheSessionKeyspace() {
        CqlSession other = CassandraTestSupport.sessionForKeyspace(OTHER_KEYSPACE);
        other.execute("TRUNCATE bets");
        other.execute("TRUNCATE bets_by_id");
        BetWriteRepository otherRepository = new BetWriteRepository(other);
        Bet a = bet("A");

        otherRepository.upsert(new BetRecord(ACCOUNT_ID, BET_ID, Instant.ofEpochMilli(1), a, 1000L));

        assertThat(BetBlobCodec.deserialize(other.execute(other.prepare(
                "SELECT bet FROM bets WHERE account_id = ? AND bet_id = ?").bind(ACCOUNT_ID, BET_ID)).one()
                .getByteBuffer("bet"))).contains(a);
        assertThat(other.execute(other.prepare("SELECT bet FROM bets_by_id WHERE bet_id = ?").bind(BET_ID)).all())
                .hasSize(1);
        assertNoRows();

        otherRepository.delete(ACCOUNT_ID, BET_ID, OptionalLong.of(2000L));

        assertThat(other.execute(other.prepare("SELECT bet FROM bets_by_id WHERE bet_id = ?").bind(BET_ID)).all())
                .isEmpty();
    }

    private void assertNoRows() {
        assertThat(rows("SELECT * FROM sports_mutations_dataloader.bets WHERE account_id = ? AND bet_id = ?", ACCOUNT_ID, BET_ID)).isEmpty();
        assertThat(rows("SELECT * FROM sports_mutations_dataloader.bets_by_id WHERE bet_id = ?", BET_ID)).isEmpty();
    }

    private java.util.Optional<Bet> storedBet(String cql, Object... values) {
        List<Row> rows = rows(cql, values);
        assertThat(rows).hasSize(1);
        return BetBlobCodec.deserialize(rows.get(0).getByteBuffer("bet"));
    }

    private List<Row> rows(String cql, Object... values) {
        return session.execute(session.prepare(cql).bind(values)).all();
    }

    private static Bet largeBet(String receiptId, int padding) {
        return Bet.newBuilder().setBetReceiptId(receiptId + "x".repeat(padding)).setCurrency("GBP").build();
    }

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
