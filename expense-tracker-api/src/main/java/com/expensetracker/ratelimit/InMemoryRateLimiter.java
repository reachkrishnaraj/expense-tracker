package com.expensetracker.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitResult tryConsume(String key, int capacity, double refillPerSecond) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(capacity, refillPerSecond));
        return bucket.tryConsume();
    }
}
