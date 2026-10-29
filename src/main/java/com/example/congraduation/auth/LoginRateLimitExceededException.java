package com.example.congraduation.auth;

import java.time.Duration;

public class LoginRateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public LoginRateLimitExceededException(Duration retryAfter) {
        super("로그인 시도가 너무 많습니다. " + toMinutes(retryAfter) + "분 후 다시 시도해 주세요.");
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    private static long toMinutes(Duration retryAfter) {
        return Math.max(1, (retryAfter.toSeconds() + 59) / 60);
    }
}
