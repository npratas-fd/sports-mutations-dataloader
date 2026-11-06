package com.flutter.gbp.betstore.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.flutter.gbp.fcq.bet.Bet;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BetReadRepositoryIT {

    private static final long ACCOUNT_1 = 1L;
    private static final long ACCOUNT_2 = 2L;

    private final CqlSession session = CassandraTestSupport.session();
    private final BetWriteRepository writer = new BetWriteRepository(session);
    private final BetReadRepository reader = new BetReadRepository(session, 100);

    @BeforeEach
    void cleanTables() {
        CassandraTestSupport.truncateAll();
    }

    @Test
    void findByAccountReturnsAllAccountBets() {
        write(ACCOUNT_1, "a");
        write(ACCOUNT_1, "b");
        write(ACCOUNT_1, "c");
        write(ACCOUNT_2, "z");

        assertThat(receipts(reader.findByAccount(ACCOUNT_1))).containsExactly("a", "b", "c");
    }

    @Test
    void findByAccountRespectsLimitAndOrder() {
        write(ACCOUNT_1, "c");
        write(ACCOUNT_1, "a");
        write(ACCOUNT_1, "b");

        BetReadRepository limited = new BetReadRepository(session, 2);

        assertThat(receipts(limited.findByAccount(ACCOUNT_1))).containsExactly("a", "b");
    }

    @Test
    void findByAccountAndBetIdsFiltersWithinAccount() {
        write(ACCOUNT_1, "a");
        write(ACCOUNT_2, "x");

        assertThat(receipts(reader.findByAccountAndBetIds(ACCOUNT_1, Set.of("a", "x")))).containsExactly("a");
    }

    @Test
    void findByAccountAndBetIdsWithEmptySetReturnsEmpty() {
        write(ACCOUNT_1, "a");

        assertThat(reader.findByAccountAndBetIds(ACCOUNT_1, Set.of())).isEmpty();
    }

    @Test
    void findByAccountAndBetIdsRespectsLimitAndOrder() {
        write(ACCOUNT_1, "c");
        write(ACCOUNT_1, "a");
        write(ACCOUNT_1, "b");

        BetReadRepository limited = new BetReadRepository(session, 2);

        assertThat(receipts(limited.findByAccountAndBetIds(ACCOUNT_1, new LinkedHashSet<>(List.of("c", "a", "b")))))
                .containsExactly("a", "b");
    }

    @Test
    void findByBetIdsAcrossAccounts() {
        write(ACCOUNT_2, "z");
        write(ACCOUNT_1, "a");

        assertThat(receipts(reader.findByBetIds(new LinkedHashSet<>(List.of("z", "a", "missing"))))).containsExactly("a", "z");
    }

    @Test
    void findByBetIdsWithEmptyInputReturnsEmptyList() {
        assertThat(reader.findByBetIds(Set.of())).isEmpty();
    }

    @Test
    void corruptBlobIsSkipped() {
        write(ACCOUNT_1, "a");
        write(ACCOUNT_1, "c");
        ByteBuffer corrupt = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5});
        session.execute(session.prepare(
                "INSERT INTO bet_store.bets (account_id, bet_id, bet_stream_timestamp, bet) VALUES (?, ?, ?, ?)")
                .bind(ACCOUNT_1, "b", Instant.ofEpochMilli(1), corrupt));

        assertThat(receipts(reader.findByAccount(ACCOUNT_1))).containsExactly("a", "c");
    }

    @Test
    void readsComeFromTheSessionKeyspace() {
        write(ACCOUNT_1, "a");
        CqlSession other = CassandraTestSupport.sessionForKeyspace("bet_store_other");
        other.execute("TRUNCATE bets");
        other.execute("TRUNCATE bets_by_id");
        Bet otherBet = Bet.newBuilder().setBetReceiptId("o").setCurrency("EUR").build();
        new BetWriteRepository(other).upsert(new BetRecord(ACCOUNT_1, "o", Instant.ofEpochMilli(1), otherBet, 1000L));

        BetReadRepository otherReader = new BetReadRepository(other, 100);

        assertThat(receipts(otherReader.findByAccount(ACCOUNT_1))).containsExactly("o");
        assertThat(receipts(otherReader.findByAccountAndBetIds(ACCOUNT_1, Set.of("a", "o")))).containsExactly("o");
        assertThat(receipts(otherReader.findByBetIds(Set.of("a", "o")))).containsExactly("o");
    }

    @Test
    void emptyAccountReturnsEmptyList() {
        assertThat(reader.findByAccount(999L)).isEmpty();
    }

    private void write(long accountId, String betId) {
        Bet bet = Bet.newBuilder().setBetReceiptId(betId).setCurrency("GBP").build();
        writer.upsert(new BetRecord(accountId, betId, Instant.ofEpochMilli(1), bet, 1000L));
    }

    private static List<String> receipts(List<Bet> bets) {
        return bets.stream().map(Bet::getBetReceiptId).toList();
    }
}
