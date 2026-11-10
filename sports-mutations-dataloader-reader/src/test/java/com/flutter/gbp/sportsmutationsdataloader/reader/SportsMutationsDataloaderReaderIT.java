package com.flutter.gbp.sportsmutationsdataloader.reader;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.flutter.gbp.sportsmutationsdataloader.api.BetList;
import com.flutter.gbp.sportsmutationsdataloader.cassandra.BetRecord;
import com.flutter.gbp.sportsmutationsdataloader.cassandra.BetWriteRepository;
import com.flutter.gbp.sportsmutationsdataloader.cassandra.CassandraTestSupport;
import com.flutter.gbp.fcq.bet.Bet;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
@SpringBootTest(classes = SportsMutationsDataloaderReaderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SportsMutationsDataloaderReaderIT {

    private static final long ACCOUNT_ID = 4242L;
    private static final long OTHER_ACCOUNT_ID = 99L;

    @LocalServerPort
    private int port;

    @Autowired
    private CqlSession session;

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

    /**
     * Like FCQ ({@code ORDER BY bet_id ASC LIMIT 70}), the account endpoint accepts more than 70 ids and returns at
     * most 70 bets, in ascending bet id order. Only {@code /v1/bets} is limited to 70 ids.
     */
    @Test
    void accountQueryWithMoreThan70IdsReturnsTheFirst70BetsByBetId() {
        BetWriteRepository writer = new BetWriteRepository(CassandraTestSupport.session());
        List<String> stored = IntStream.range(0, 75).mapToObj(i -> String.format("R/CAP/%04d", i)).toList();
        // Written in reverse order: the result order must come from Cassandra's clustering order, not insertion
        for (String id : stored.reversed()) {
            writer.upsert(new BetRecord(ACCOUNT_ID, id, Instant.ofEpochMilli(1), bet(id), 1000L));
        }
        List<String> requested = new ArrayList<>(stored.reversed());
        requested.addAll(List.of("R/CAP/missing-1", "R/CAP/missing-2"));

        assertThat(get("/v1/accounts/" + ACCOUNT_ID + "/bets?betIds=" + betIdsParam(requested)).getBetsList())
                .extracting(Bet::getBetReceiptId).containsExactlyElementsOf(stored.subList(0, 70));
        assertThat(get("/v1/accounts/" + ACCOUNT_ID + "/bets").getBetsList())
                .extracting(Bet::getBetReceiptId).containsExactlyElementsOf(stored.subList(0, 70));
    }

    @Test
    void betIdsQueryWithMoreThan70IdsIsRejectedWith400() {
        List<String> ids = IntStream.range(0, 71).mapToObj(i -> "R/CAP/" + i).toList();

        assertThat(status("/v1/bets?betIds=" + betIdsParam(ids))).isEqualTo(400);
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
        List<String> thousand = IntStream.range(0, 1000).mapToObj(i -> String.format("O/123456789/%04d", i)).toList();
        List<String> tooMany = new ArrayList<>(thousand);
        tooMany.add("O/123456789/1000");

        assertThat(get("/v1/accounts/" + ACCOUNT_ID + "/bets?betIds=" + betIdsParam(thousand)).getBetsList())
                .extracting(Bet::getBetReceiptId).containsExactly("O/123456789/0007");
        assertThat(status("/v1/accounts/" + ACCOUNT_ID + "/bets?betIds=" + betIdsParam(tooMany))).isEqualTo(400);
    }

    /** LOCAL_QUORUM like FCQ: LOCAL_ONE could serve a settled bet from a replica that has not seen the delete. */
    @Test
    void cassandraQueriesUseLocalQuorum() {
        assertThat(session.getContext().getConfig().getDefaultProfile()
                .getString(DefaultDriverOption.REQUEST_CONSISTENCY)).isEqualTo("LOCAL_QUORUM");
    }

    @Test
    void healthReportsCassandraUp() {
        String health = text("/actuator/health");

        assertThat(health).contains("\"status\":\"UP\"").contains("\"cassandra\":{\"status\":\"UP\"");
    }

    /** http.server.requests is published as a histogram per endpoint (tag uri), aggregable across instances. */
    @Test
    void prometheusPublishesRequestLatencyHistogramPerEndpoint() {
        get("/v1/accounts/" + ACCOUNT_ID + "/bets?betIds=a,b");

        String metrics = text("/actuator/prometheus");

        assertThat(metrics).containsPattern(
                "http_server_requests_seconds_bucket\\{[^}]*uri=\"/v1/accounts/\\{accountId\\}/bets\"[^}]*le=\"");
        assertThat(metrics).contains("sports_mutations_dataloader_reader_bet_ids_requested_count{endpoint=\"account\"}")
                .contains("sports_mutations_dataloader_reader_bets_returned_count{endpoint=\"account\"}");
    }

    private String text(String path) {
        return RestClient.create().get()
                .uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .retrieve()
                .body(String.class);
    }

    private int status(String path) {
        return RestClient.create().get()
                .uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    /** Wire format of the {@code betIds} parameter: each id URL-encoded, joined by a literal comma. */
    private static String betIdsParam(List<String> ids) {
        return ids.stream().map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8)).collect(Collectors.joining(","));
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
