package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmDebugRateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Clock clock;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public LlmDebugRateLimiter() {
        this(Clock.systemUTC());
    }

    LlmDebugRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String userId, String providerKey) {
        String key = (userId == null || userId.isBlank() ? "anonymous" : userId)
                + ":"
                + (providerKey == null || providerKey.isBlank() ? "draft" : providerKey);
        long now = clock.millis();
        Deque<Long> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() >= WINDOW_MILLIS) {
                bucket.removeFirst();
            }
            if (bucket.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new BusinessException(429, "调试请求过于频繁，请稍后再试");
            }
            bucket.addLast(now);
        }
    }
}
