package com.sense.prolt_operations.security.ratelimit;

public class RateLimiterUnavailableException extends RuntimeException {

    public RateLimiterUnavailableException(String message) {
        super(message);
    }

    public RateLimiterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
