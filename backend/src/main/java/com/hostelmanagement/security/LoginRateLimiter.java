package com.hostelmanagement.security;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window limiter for sensitive unauthenticated endpoints.
 * Suitable for a single instance; a shared store (e.g. Redis) would be needed when scaling out.
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_MS = 60_000;

    private final int maxPerWindow;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public LoginRateLimiter(AppProperties props) {
        this.maxPerWindow = Math.max(1, props.security().loginRateLimitPerMinute());
    }

    public void check(String key) {
        check(key, maxPerWindow);
    }

    /** Same as {@link #check(String)} with an explicit per-minute limit (e.g. higher for token refresh). */
    public void check(String key, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many attempts. Please wait a minute and try again.");
            }
            q.addLast(now);
        }
        if (hits.size() > 10_000) {
            hits.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }
}
