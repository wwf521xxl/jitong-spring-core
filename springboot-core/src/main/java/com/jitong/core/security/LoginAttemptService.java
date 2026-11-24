package com.jitong.core.security;

import com.jitong.core.common.ApiException;
import com.jitong.core.config.JitongProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 进程内登录限流：小型单实例部署足够；多实例时可替换为 Redis。 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final long windowSeconds;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(JitongProperties properties, Clock clock) {
        this.maxAttempts = properties.getSecurity().getLoginMaxAttempts();
        this.windowSeconds = properties.getSecurity().getLoginWindow().getSeconds();
        this.clock = clock;
    }

    public void check(String key) {
        if (maxAttempts <= 0) {
            return;
        }
        Deque<Instant> queue = attempts.get(key);
        if (queue == null) {
            return;
        }
        synchronized (queue) {
            prune(queue);
            if (queue.size() >= maxAttempts) {
                throw ApiException.tooManyRequests("登录尝试过于频繁，请稍后再试");
            }
        }
    }

    public void failure(String key) {
        if (maxAttempts <= 0) {
            return;
        }
        Deque<Instant> queue = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            prune(queue);
            queue.addLast(clock.instant());
        }
    }

    public void success(String key) {
        attempts.remove(key);
    }

    private void prune(Deque<Instant> queue) {
        Instant cutoff = clock.instant().minus(windowSeconds, ChronoUnit.SECONDS);
        while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
            queue.removeFirst();
        }
    }
}
