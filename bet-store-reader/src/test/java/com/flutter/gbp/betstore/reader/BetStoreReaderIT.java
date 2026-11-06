package com.flutter.gbp.betstore.reader;

import static org.assertj.core.api.Assertions.assertThat;

import com.flutter.gbp.betstore.api.BetList;
import com.flutter.gbp.betstore.cassandra.BetRecord;
import com.flutter.gbp.betstore.cassandra.BetWriteRepository;
import com.flutter.gbp.betstore.cassandra.CassandraTestSupport;
import com.flutter.gbp.fcq.bet.Bet;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * End-to-end test of the reader: HTTP → controller → Cassandra (shared container of {@link CassandraTestSupport}).
 */
@SpringBootTest(classes = BetStoreReaderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BetStoreReaderIT {

    private static final long ACCOUNT_ID = 4242L;
    private static final long OTHER_ACCOUNT_ID = 99L;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void cassandra(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points",
                () -> CassandraTestSupport.contactPoint().getHostString() + ":" + CassandraTestSupport.contactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", CassandraTestSupport::localDatacenter);
    }

    @BeforeEach
    void seed() {
        CassandraTestSupport.truncateAll();
    }

    @Test
    void servesWrittenBetsByAccountAndById() {
        BetWriteRepository writer = new BetWriteRepository(CassandraTestSupport.session());
        Bet first = bet("R/IT/0001");
        Bet second = bet("R/IT/0002");
        Bet other = bet("R/IT/0003");
        writer.upsert(new BetRecord(ACCOUNT_ID, "R/IT/0001", Instant.ofEpochMilli(1), first, 1000L));
        writer.upsert(new BetRecord(ACCOUNT_ID, "R/IT/0002", Instant.ofEpochMilli(1), second, 1000L));
        writer.upsert(new BetRecord(OTHER_ACCOUNT_ID, "R/IT/0003", Instant.ofEpochMilli(1), other, 1000L));

        assertThat(get("/v1/accounts/" + ACCOUNT_ID + "/bets").getBetsList()).containsExactly(first, second);
        assertThat(get("/v1/accounts/" + ACCOUNT_ID + "/bets?betIds=R/IT/0002,R/IT/0003").getBetsList())
                .containsExactly(second);
        assertThat(get("/v1/bets?betIds=R/IT/0003,R/IT/0001,missing").getBetsList()).containsExactly(first, other);
        assertThat(get("/v1/accounts/1/bets").getBetsList()).isEqualTo(List.of());
    }

    @Test
    void healthReportsCassandraUp() {
        String health = text("/actuator/health");

        assertThat(health).contains("\"status\":\"UP\"").contains("\"cassandra\":{\"status\":\"UP\"");
    }

    /** p50/p95/p99 of http.server.requests per endpoint (tag uri) are published for Prometheus / Datadog. */
    @Test
    void prometheusPublishesRequestLatencyPercentilesPerEndpoint() {
        get("/v1/accounts/" + ACCOUNT_ID + "/bets");

        String metrics = text("/actuator/prometheus");

        for (String quantile : List.of("0.5", "0.95", "0.99")) {
            assertThat(metrics).containsPattern(
                    "http_server_requests_seconds\\{[^}]*quantile=\"" + quantile + "\"[^}]*uri=\"/v1/accounts/\\{accountId\\}/bets\"|"
                            + "http_server_requests_seconds\\{[^}]*uri=\"/v1/accounts/\\{accountId\\}/bets\"[^}]*quantile=\"" + quantile + "\"");
        }
    }

    private String text(String path) {
        return RestClient.create().get()
                .uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .retrieve()
                .body(String.class);
    }

    private BetList get(String path) {
        byte[] body = RestClient.create().get()
                .uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .retrieve()
                .body(byte[].class);
        try {
            // An empty BetList serializes to zero bytes, which RestClient reports as a null body
            return BetList.parseFrom(body == null ? new byte[0] : body);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
