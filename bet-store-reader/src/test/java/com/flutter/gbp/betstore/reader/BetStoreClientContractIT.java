package com.flutter.gbp.betstore.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flutter.gbp.betstore.cassandra.BetRecord;
import com.flutter.gbp.betstore.cassandra.BetWriteRepository;
import com.flutter.gbp.betstore.cassandra.CassandraTestSupport;
import com.flutter.gbp.betstore.client.BetStoreClient;
import com.flutter.gbp.betstore.client.BetStoreClientConfig;
import com.flutter.gbp.betstore.client.BetStoreClientException;
import com.flutter.gbp.fcq.bet.Bet;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Contract test: the real {@link BetStoreClient} (Java 11, used by FCQ) against the real reader over HTTP.
 */
@SpringBootTest(classes = BetStoreReaderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BetStoreClientContractIT {

    private static final long ACCOUNT_ID = 5151L;
    private static final long OTHER_ACCOUNT_ID = 52L;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void cassandra(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points",
                () -> CassandraTestSupport.contactPoint().getHostString() + ":" + CassandraTestSupport.contactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", CassandraTestSupport::localDatacenter);
    }

    @BeforeEach
    void reset() {
        CassandraTestSupport.truncateAll();
    }

    @Test
    void clientReadsWhatWasWritten() {
        BetWriteRepository writer = new BetWriteRepository(CassandraTestSupport.session());
        Bet first = bet("R/CT/0001");
        Bet second = bet("R/CT/0002");
        Bet other = bet("R/CT/0003");
        writer.upsert(new BetRecord(ACCOUNT_ID, "R/CT/0001", Instant.ofEpochMilli(1), first, 1000L));
        writer.upsert(new BetRecord(ACCOUNT_ID, "R/CT/0002", Instant.ofEpochMilli(1), second, 1000L));
        writer.upsert(new BetRecord(OTHER_ACCOUNT_ID, "R/CT/0003", Instant.ofEpochMilli(1), other, 1000L));

        BetStoreClient client = new BetStoreClient(new BetStoreClientConfig(
                URI.create("http://localhost:" + port), Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(client.getBets(ACCOUNT_ID).join()).containsExactly(first, second);
        assertThat(client.getBets(ACCOUNT_ID, Set.of("R/CT/0002", "R/CT/0003")).join()).containsExactly(second);
        assertThat(client.getBetsByIds(Set.of("R/CT/0003", "R/CT/0001", "missing")).join())
                .containsExactlyInAnyOrder(first, other);
        assertThat(client.getBets(1L).join()).isEmpty();
    }

    /**
     * Like FCQ ({@code ORDER BY bet_id ASC LIMIT 70}), the account endpoint accepts more than 70 ids and returns at
     * most 70 bets, in ascending bet id order. Only {@code getBetsByIds} is limited to 70 ids.
     */
    @Test
    void accountQueryWithMoreThan70IdsReturnsTheFirst70BetsByBetId() {
        BetWriteRepository writer = new BetWriteRepository(CassandraTestSupport.session());
        List<String> stored = IntStream.range(0, 75).mapToObj(i -> String.format("R/CAP/%04d", i)).toList();
        // Written in reverse order: the result order must come from Cassandra's clustering order, not insertion
        for (String id : stored.reversed()) {
            writer.upsert(new BetRecord(ACCOUNT_ID, id, Instant.ofEpochMilli(1), bet(id), 1000L));
        }
        Set<String> requested = new LinkedHashSet<>(stored.reversed());
        requested.addAll(List.of("R/CAP/missing-1", "R/CAP/missing-2"));
        BetStoreClient client = client();

        List<Bet> bets = client.getBets(ACCOUNT_ID, requested).join();

        assertThat(bets).extracting(Bet::getBetReceiptId).containsExactlyElementsOf(stored.subList(0, 70));
        assertThat(client.getBets(ACCOUNT_ID).join()).extracting(Bet::getBetReceiptId)
                .containsExactlyElementsOf(stored.subList(0, 70));
    }

    @Test
    void betIdsQueryWithMoreThan70IdsIsRejectedWith400() {
        Set<String> ids = IntStream.range(0, 71).mapToObj(i -> "R/CAP/" + i).collect(Collectors.toSet());

        assertThatThrownBy(() -> client().getBetsByIds(ids).join())
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(400));
    }

    /**
     * 1000 FCQ-style ids (~21 KB once URL-encoded) exceed the default 8 KB request header limit, hence
     * {@code server.max-http-request-header-size: 64KB}. Above {@code max-account-bet-ids} (1000) the reader
     * answers 400.
     */
    @Test
    void accountQueryAcceptsUpTo1000IdsAndRejectsMoreWith400() {
        BetWriteRepository writer = new BetWriteRepository(CassandraTestSupport.session());
        writer.upsert(new BetRecord(ACCOUNT_ID, "O/123456789/0007", Instant.ofEpochMilli(1), bet("O/123456789/0007"),
                1000L));
        Set<String> thousand = IntStream.range(0, 1000).mapToObj(i -> String.format("O/123456789/%04d", i))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> tooMany = new LinkedHashSet<>(thousand);
        tooMany.add("O/123456789/1000");
        BetStoreClient client = client();

        assertThat(client.getBets(ACCOUNT_ID, thousand).join()).extracting(Bet::getBetReceiptId)
                .containsExactly("O/123456789/0007");
        assertThatThrownBy(() -> client.getBets(ACCOUNT_ID, tooMany).join())
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(400));
    }

    private BetStoreClient client() {
        return new BetStoreClient(new BetStoreClientConfig(
                URI.create("http://localhost:" + port), Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
