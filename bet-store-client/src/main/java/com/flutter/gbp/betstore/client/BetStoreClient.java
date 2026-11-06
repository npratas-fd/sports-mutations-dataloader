package com.flutter.gbp.betstore.client;

import static com.google.common.base.Preconditions.checkArgument;

import com.flutter.gbp.betstore.api.BetList;
import com.flutter.gbp.fcq.bet.Bet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * HTTP client of the bet-store reader (protobuf over HTTP/1.1 with keep-alive).
 *
 * <p>Every method returns a future that completes exceptionally with a {@link BetStoreClientException} on failure:
 * {@code statusCode()} is the HTTP status for a non-2xx response and {@code -1} for I/O errors and timeouts.
 * {@link CompletableFuture#join()} wraps that exception in a {@link CompletionException}; {@code get()} wraps it in
 * an {@code ExecutionException}. An empty 200 body is an empty list.
 */
public class BetStoreClient {

    private static final String PROTOBUF = "application/x-protobuf";

    private final BetStoreClientConfig config;
    private final String base;
    private final HttpClient httpClient;

    public BetStoreClient(BetStoreClientConfig config) {
        checkArgument(config != null, "config must not be null");
        this.config = config;
        String uri = config.baseUri().toString();
        this.base = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(config.connectTimeout())
                .build();
    }

    /** All open bets of an account. */
    public CompletableFuture<List<Bet>> getBets(long accountId) {
        return fetch(base + accountPath(accountId));
    }

    /** The given bets of an account; bets that do not exist or belong to another account are omitted. */
    public CompletableFuture<List<Bet>> getBets(long accountId, Set<String> betIds) {
        checkArgument(betIds != null, "betIds must not be null");
        if (betIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return fetch(base + accountPath(accountId) + "?betIds=" + encode(betIds));
    }

    /** The given bets regardless of account; bets that do not exist are omitted. */
    public CompletableFuture<List<Bet>> getBetsByIds(Set<String> betIds) {
        checkArgument(betIds != null, "betIds must not be null");
        if (betIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return fetch(base + "/v1/bets?betIds=" + encode(betIds));
    }

    private static String accountPath(long accountId) {
        return "/v1/accounts/" + accountId + "/bets";
    }

    /** Each id is URL-encoded; ids are separated by a literal comma, as the reader expects. */
    private static String encode(Set<String> betIds) {
        return betIds.stream()
                .map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
    }

    private CompletableFuture<List<Bet>> fetch(String url) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("Accept", PROTOBUF)
                    .timeout(config.requestTimeout())
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new BetStoreClientException(-1, "invalid request URI: " + url, e));
        }
        CompletableFuture<List<Bet>> result = new CompletableFuture<>();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                result.completeExceptionally(new BetStoreClientException(-1,
                        "bet-store request failed: " + cause, cause));
                return;
            }
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                result.completeExceptionally(new BetStoreClientException(status,
                        "bet-store responded with HTTP " + status));
                return;
            }
            try {
                result.complete(BetList.parseFrom(response.body()).getBetsList());
            } catch (InvalidProtocolBufferException e) {
                result.completeExceptionally(new BetStoreClientException(-1,
                        "bet-store returned an unparseable body", e));
            }
        });
        return result;
    }
}
