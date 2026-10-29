package com.example.congraduation.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키별 고정 윈도우 카운터. 윈도우는 키의 첫 시도 시점부터 시작한다.
 * 단일 인스턴스(EC2 1대) 운영을 전제로 메모리에만 보관한다.
 */
final class FixedWindowCounter {

    private static final int PURGE_THRESHOLD = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    FixedWindowCounter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * 한도 안이면 카운트를 1 올리고 empty, 한도를 넘었으면 윈도우가 끝날 때까지 남은 시간을 반환한다.
     */
    Optional<Duration> tryAcquire(String key) {
        if (windows.size() > PURGE_THRESHOLD) {
            purgeExpired();
        }
        long now = clock.millis();
        long[] retryAfterMillis = {-1};
        windows.compute(key, (k, current) -> {
            if (current == null || current.isExpired(now, windowMillis)) {
                return new Window(now, 1);
            }
            if (current.count() >= limit) {
                retryAfterMillis[0] = current.startMillis() + windowMillis - now;
                return current;
            }
            return new Window(current.startMillis(), current.count() + 1);
        });
        return retryAfterMillis[0] < 0 ? Optional.empty() : Optional.of(Duration.ofMillis(retryAfterMillis[0]));
    }

    /** 시도가 판정 불가(포털 장애 등)로 끝났을 때 차감한다. */
    void release(String key) {
        long now = clock.millis();
        windows.computeIfPresent(key, (k, current) -> {
            if (current.isExpired(now, windowMillis) || current.count() <= 1) {
                return null;
            }
            return new Window(current.startMillis(), current.count() - 1);
        });
    }

    void reset(String key) {
        windows.remove(key);
    }

    void purgeExpired() {
        long now = clock.millis();
        windows.values().removeIf(window -> window.isExpired(now, windowMillis));
    }

    int trackedKeyCount() {
        return windows.size();
    }

    private record Window(long startMillis, int count) {
        boolean isExpired(long now, long windowMillis) {
            return now - startMillis >= windowMillis;
        }
    }
}
