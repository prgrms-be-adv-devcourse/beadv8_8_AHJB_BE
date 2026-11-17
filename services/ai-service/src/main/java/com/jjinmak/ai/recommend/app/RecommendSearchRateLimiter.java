package com.jjinmak.ai.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.jjinmak.ai.recommend.exception.RecommendErrorCode.SEARCH_TOO_MANY_REQUESTS;

@Component
public class RecommendSearchRateLimiter {

    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<Long, Deque<Instant>> callTimes = new ConcurrentHashMap<>();

    public void check(Long memberId){
        Deque<Instant> times = callTimes.computeIfAbsent(memberId, id -> new ArrayDeque<>());

        synchronized (times) {
            Instant now = Instant.now();
            Instant windowStart = now.minus(WINDOW);

            while (!times.isEmpty() && times.peekFirst().isBefore(windowStart)) {
                times.pollFirst();
            }

            if (times.size() >= LIMIT)
                throw new BusinessException(SEARCH_TOO_MANY_REQUESTS);

            times.addLast(now);
        }
    }
}
