package com.flutter.gbp.betstore.client;

/**
 * Failure of a bet-store call.
 *
 * <p>{@link #statusCode()} is the HTTP status of a non-2xx response, or {@code -1} when no usable response was
 * obtained (I/O error, connection failure, timeout, or an unparseable body).
 */
public class BetStoreClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public BetStoreClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public BetStoreClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code of the failed response, or {@code -1} for I/O errors and timeouts. */
    public int statusCode() {
        return statusCode;
    }
}
