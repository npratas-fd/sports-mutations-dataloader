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

    /** Open bets of an account: at most 70 ({@code bet-store.reader.bet-limit}), lowest bet ids first. */
    public CompletableFuture<List<Bet>> getBets(long accountId) {
        return fetch(base + accountPath(accountId));
    }

    /**
     * The given bets of an account; bets that do not exist or belong to another account are omitted.
     *
     * <p>Like FCQ's own query ({@code ORDER BY bet_id ASC LIMIT 70}), any number of ids may be asked for and at most
     * 70 bets ({@code bet-store.reader.bet-limit}) are returned: those with the lowest bet ids, in ascending bet id
     * order. Only above 1000 distinct ids ({@code bet-store.reader.max-account-bet-ids}, abuse protection) does the
     * reader answer 400, i.e. a {@link BetStoreClientException} with {@code statusCode() == 400}. An empty set returns
     * an empty list without a request. A null, blank or comma-containing id (commas separate ids on the wire) completes
     * the future exceptionally with an {@link IllegalArgumentException}.
     */
    public CompletableFuture<List<Bet>> getBets(long accountId, Set<String> betIds) {
        checkArgument(betIds != null, "betIds must not be null");
        if (betIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String invalid = invalidBetId(betIds);
        if (invalid != null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(invalid));
        }
        return fetch(base + accountPath(accountId) + "?betIds=" + encode(betIds));
    }

    /**
     * The given bets regardless of account, in ascending bet id order; bets that do not exist are omitted.
     *
     * <p>Unlike {@link #getBets(long, Set)}, this endpoint accepts at most 70 distinct ids per request
     * ({@code bet-store.reader.max-bet-ids}); more yields a {@link BetStoreClientException} with
     * {@code statusCode() == 400}. An empty set returns an empty list without a request. A null, blank or
     * comma-containing id (commas separate ids on the wire) completes the future exceptionally with an
     * {@link IllegalArgumentException}.
     */
    public CompletableFuture<List<Bet>> getBetsByIds(Set<String> betIds) {
        checkArgument(betIds != null, "betIds must not be null");
        if (betIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String invalid = invalidBetId(betIds);
        if (invalid != null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(invalid));
        }
        return fetch(base + "/v1/bets?betIds=" + encode(betIds));
    }

    private static String accountPath(long accountId) {
        return "/v1/accounts/" + accountId + "/bets";
    }

    /** Returns an error message for the first unusable id, or null when all ids are valid. */
    private static String invalidBetId(Set<String> betIds) {
        for (String id : betIds) {
            if (id == null || id.trim().isEmpty()) {
                return "betIds must not contain null or blank ids";
            }
            if (id.indexOf(',') >= 0) {
                return "betIds must not contain ',' (it separates ids on the wire): " + id;
            }
        }
        return null;
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
