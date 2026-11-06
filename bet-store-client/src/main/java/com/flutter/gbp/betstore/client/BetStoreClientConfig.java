package com.flutter.gbp.betstore.client;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URI;
import java.time.Duration;

/** Connection settings of the {@link BetStoreClient}. */
public final class BetStoreClientConfig {

    private final URI baseUri;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    /**
     * @param baseUri        base URI of the bet-store reader, e.g. {@code http://bet-store-reader:8080}
     * @param connectTimeout maximum time to establish the connection
     * @param requestTimeout maximum time to wait for each response
     */
    public BetStoreClientConfig(URI baseUri, Duration connectTimeout, Duration requestTimeout) {
        checkArgument(baseUri != null && baseUri.getHost() != null, "baseUri must be an absolute http(s) URI");
        checkArgument(connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero(),
                "connectTimeout must be positive");
        checkArgument(requestTimeout != null && !requestTimeout.isNegative() && !requestTimeout.isZero(),
                "requestTimeout must be positive");
        this.baseUri = baseUri;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }

    public URI baseUri() {
        return baseUri;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }
}
