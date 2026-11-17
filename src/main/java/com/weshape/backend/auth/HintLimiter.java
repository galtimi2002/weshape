package com.weshape.backend.auth;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most {@code max} lookups per client in a sliding window, so the hint cannot be used to test a
 * long list of email addresses for Google accounts.
 */
public class HintLimiter {

    private final int max;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    public HintLimiter(int max, long windowMs, Clock clock) {
        this.max = max;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /** True when this client may make one more lookup now (and counts it). */
    public boolean allow(String client) {
        long now = clock.millis();
        // Forget clients that have been quiet for a whole window, so the map does not grow.
        if (calls.size() > 10_000) {
            calls.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast() <= now - windowMs;
                }
            });
        }
        Deque<Long> times = calls.computeIfAbsent(client, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= now - windowMs) times.pollFirst();
            if (times.size() >= max) return false;
            times.addLast(now);
            return true;
        }
    }
}
