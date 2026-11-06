package com.flutter.gbp.betstore.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flutter.gbp.betstore.api.BetList;
import com.flutter.gbp.fcq.bet.Bet;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BetStoreClientTest {

    private WireMockServer server;
    private BetStoreClient client;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new BetStoreClient(new BetStoreClientConfig(
                URI.create("http://localhost:" + server.port()), Duration.ofSeconds(2), Duration.ofMillis(500)));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void getBetsByAccountParsesProtobuf() {
        server.stubFor(get(urlPathEqualTo("/v1/accounts/42/bets"))
                .withHeader("Accept", equalTo("application/x-protobuf"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/x-protobuf")
                        .withBody(betList("R/1", "R/2").toByteArray())));

        List<Bet> bets = client.getBets(42L).join();

        assertThat(bets).containsExactly(bet("R/1"), bet("R/2"));
    }

    @Test
    void emptyBodyIsEmptyList() {
        server.stubFor(get(urlPathEqualTo("/v1/accounts/42/bets")).willReturn(aResponse().withStatus(200)));

        assertThat(client.getBets(42L).join()).isEmpty();
    }

    @Test
    void getBetsWithIdsSendsEncodedIds() {
        server.stubFor(get(urlPathEqualTo("/v1/accounts/42/bets"))
                .willReturn(aResponse().withStatus(200).withBody(betList("a/b").toByteArray())));

        List<Bet> bets = client.getBets(42L, new TreeSet<>(Set.of("a/b", "c d"))).join();

        assertThat(bets).containsExactly(bet("a/b"));
        String url = server.findAll(getRequestedFor(urlPathEqualTo("/v1/accounts/42/bets"))).get(0).getUrl();
        assertThat(url).isEqualTo("/v1/accounts/42/bets?betIds=a%2Fb,c+d");
    }

    @Test
    void invalidBetIdsFailTheFutureWithoutRequest() {
        Set<String> withNull = new HashSet<>(Arrays.asList("a", null));
        for (Set<String> ids : List.of(Set.of("a,b"), withNull, Set.of(" "))) {
            for (CompletableFuture<List<Bet>> future : List.of(client.getBets(42L, ids), client.getBetsByIds(ids))) {
                assertThatThrownBy(future::join)
                        .isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void getBetsByIdsUsesGlobalEndpoint() {
        server.stubFor(get(urlPathEqualTo("/v1/bets"))
                .willReturn(aResponse().withStatus(200).withBody(betList("x").toByteArray())));

        assertThat(client.getBetsByIds(Set.of("x")).join()).containsExactly(bet("x"));
    }

    @Test
    void baseUriTrailingSlashIsTolerated() {
        BetStoreClient slash = new BetStoreClient(new BetStoreClientConfig(
                URI.create("http://localhost:" + server.port() + "/"), Duration.ofSeconds(2), Duration.ofSeconds(2)));
        server.stubFor(get(urlPathEqualTo("/v1/accounts/1/bets")).willReturn(aResponse().withStatus(200)));

        assertThat(slash.getBets(1L).join()).isEmpty();
    }

    @Test
    void emptyBetIdsShortCircuits() {
        assertThat(client.getBets(42L, Set.of()).join()).isEmpty();
        assertThat(client.getBetsByIds(Set.of()).join()).isEmpty();

        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void non2xxCompletesExceptionallyWithStatus() {
        server.stubFor(get(urlPathEqualTo("/v1/accounts/42/bets")).willReturn(aResponse().withStatus(503)));

        CompletableFuture<List<Bet>> future = client.getBets(42L);

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(503));
    }

    @Test
    void timeoutCompletesExceptionally() {
        server.stubFor(get(urlPathEqualTo("/v1/accounts/42/bets"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

        CompletableFuture<List<Bet>> future = client.getBets(42L);

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(-1));
    }

    @Test
    void connectionFailureCompletesExceptionally() {
        server.stop();

        assertThatThrownBy(() -> client.getBets(42L).join())
                .hasCauseInstanceOf(BetStoreClientException.class)
                .satisfies(e -> assertThat(((BetStoreClientException) e.getCause()).statusCode()).isEqualTo(-1));
    }

    private static BetList betList(String... receiptIds) {
        BetList.Builder builder = BetList.newBuilder();
        for (String id : receiptIds) {
            builder.addBets(bet(id));
        }
        return builder.build();
    }

    private static Bet bet(String receiptId) {
        return Bet.newBuilder().setBetReceiptId(receiptId).setCurrency("GBP").build();
    }
}
