package com.expensetracker.ratelimit;

public class TokenBucket {

    private final int capacity;
    private double tokens;
    private final double refillRatePerSecond;
    private long lastRefillTimestamp;

    public TokenBucket(int capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.tokens = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.lastRefillTimestamp = System.nanoTime();
    }

    public synchronized RateLimitResult tryConsume() {
        refill();

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return new RateLimitResult(
                    true,
                    capacity,
                    (int) tokens,
                    0,
                    calculateResetTimestamp()
            );
        } else {
            long retryAfterSeconds = (long) Math.ceil((1.0 - tokens) / refillRatePerSecond);
            return new RateLimitResult(
                    false,
                    capacity,
                    0,
                    retryAfterSeconds,
                    calculateResetTimestamp()
            );
        }
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillTimestamp) / 1_000_000_000.0;
        double tokensToAdd = elapsedSeconds * refillRatePerSecond;
        tokens = Math.min(capacity, tokens + tokensToAdd);
        lastRefillTimestamp = now;
    }

    private long calculateResetTimestamp() {
        // Time in seconds until bucket is fully refilled
        double tokensNeeded = capacity - tokens;
        long secondsUntilFull = (long) Math.ceil(tokensNeeded / refillRatePerSecond);
        return System.currentTimeMillis() / 1000 + secondsUntilFull;
    }
}
