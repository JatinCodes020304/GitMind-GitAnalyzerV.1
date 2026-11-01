package com.gitmind.ratelimit;

import java.time.Clock;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * a rolling (sliding) window rate limiter.
 *
 * Unlike a fixed-window counter, which lets a client burst up to 2x the limit
 * right across a window boundary (e.g. 30 requests in the last second of minute 1
 * plus 30 more in the first second of minute 2 = 60 in ~2 seconds), this keeps a
 * timestamp log per client key and only counts requests that fall inside the
 * trailing {@code windowMillis} - so a sudden traffic spike is smoothed out
 * continuously rather than reset in a step function.
 *
 * Deliberately in-memory / per-instance: fine for a single-node deployment.
 * If GitMind is ever scaled horizontally, swap the backing store for Redis
 * (e.g. a sorted set with ZREMRANGEBYSCORE) and keep the same interface.
 */
public class RollingWindowRateLimiter {

    private final long windowMillis;
    private final int maxRequestsPerWindow;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public RollingWindowRateLimiter(int windowSeconds, int maxRequestsPerWindow) {
        this(windowSeconds, maxRequestsPerWindow, Clock.systemUTC());
    }

    RollingWindowRateLimiter(int windowSeconds, int maxRequestsPerWindow, Clock clock) {
        this.windowMillis = windowSeconds * 1000L;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clock = clock;
    }

    /**
     * @param key typically client IP (or API key, if/when auth is added)
     * @return true if the request is allowed, false if it should be rejected (429)
     */
    public synchronized boolean tryAcquire(String key) {
        long now = clock.millis();
        long windowStart = now - windowMillis;

        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // Evict anything that has aged out of the trailing window.
        while (true) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || oldest >= windowStart) {
                break;
            }
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequestsPerWindow) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    /** Seconds until the oldest request in this client's window expires, for a Retry-After hint. */
    public long secondsUntilSlotFrees(String key) {
        Deque<Long> timestamps = requestLog.get(key);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        Long oldest = timestamps.peekFirst();
        if (oldest == null) {
            return 0;
        }
        long freesAt = oldest + windowMillis;
        long remainingMs = Math.max(0, freesAt - clock.millis());
        return (remainingMs + 999) / 1000;
    }

    /** Periodic cleanup so long-idle client keys don't leak memory forever. */
    public synchronized void evictStaleKeys() {
        long now = clock.millis();
        long windowStart = now - windowMillis;
        requestLog.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            Long newest = timestamps.peekLast();
            return newest == null || newest < windowStart;
        });
    }


}
