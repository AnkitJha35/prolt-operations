package com.sense.prolt_operations.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /**
     * IP-level shared auth rate limit (across send-otp and verify-otp).
     */
    private int authIpLimit = 10;
    private long authIpWindowSeconds = 900; // 15 minutes

    /**
     * Account-level limit for OTP send requests.
     */
    private int otpSendAccountLimit = 20;
    private long otpSendAccountWindowSeconds = 900; // 15 minutes

    /**
     * Account-level limit for OTP verification attempts.
     */
    private int otpVerifyAccountLimit = 20;
    private long otpVerifyAccountWindowSeconds = 900; // 15 minutes

    /**
     * Secret key/salt used to HMAC-SHA256 normalize email addresses in Redis keys.
     */
    private String accountHmacKey;

    /**
     * Maximum time to wait for Redis rate-limiting operations before triggering timeout/fallback.
     */
    private long redisTimeoutMs = 2000;

    /**
     * Whether to emit legacy X-RateLimit-* headers in addition to standard RateLimit-* headers.
     */
    private boolean legacyHeaders = false;

    // Getters and Setters

    public int getAuthIpLimit() {
        return authIpLimit;
    }

    public void setAuthIpLimit(int authIpLimit) {
        this.authIpLimit = authIpLimit;
    }

    public long getAuthIpWindowSeconds() {
        return authIpWindowSeconds;
    }

    public void setAuthIpWindowSeconds(long authIpWindowSeconds) {
        this.authIpWindowSeconds = authIpWindowSeconds;
    }

    public int getOtpSendAccountLimit() {
        return otpSendAccountLimit;
    }

    public void setOtpSendAccountLimit(int otpSendAccountLimit) {
        this.otpSendAccountLimit = otpSendAccountLimit;
    }

    public long getOtpSendAccountWindowSeconds() {
        return otpSendAccountWindowSeconds;
    }

    public void setOtpSendAccountWindowSeconds(long otpSendAccountWindowSeconds) {
        this.otpSendAccountWindowSeconds = otpSendAccountWindowSeconds;
    }

    public int getOtpVerifyAccountLimit() {
        return otpVerifyAccountLimit;
    }

    public void setOtpVerifyAccountLimit(int otpVerifyAccountLimit) {
        this.otpVerifyAccountLimit = otpVerifyAccountLimit;
    }

    public long getOtpVerifyAccountWindowSeconds() {
        return otpVerifyAccountWindowSeconds;
    }

    public void setOtpVerifyAccountWindowSeconds(long otpVerifyAccountWindowSeconds) {
        this.otpVerifyAccountWindowSeconds = otpVerifyAccountWindowSeconds;
    }

    public String getAccountHmacKey() {
        return accountHmacKey;
    }

    public void setAccountHmacKey(String accountHmacKey) {
        this.accountHmacKey = accountHmacKey;
    }

    public long getRedisTimeoutMs() {
        return redisTimeoutMs;
    }

    public void setRedisTimeoutMs(long redisTimeoutMs) {
        this.redisTimeoutMs = redisTimeoutMs;
    }

    public boolean isLegacyHeaders() {
        return legacyHeaders;
    }

    public void setLegacyHeaders(boolean legacyHeaders) {
        this.legacyHeaders = legacyHeaders;
    }
}
