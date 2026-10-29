package com.example.congraduation.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private MutableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-29T00:00:00Z"));
        limiter = new LoginRateLimiter(
                10, Duration.ofMinutes(1),
                4, Duration.ofMinutes(15),
                3, Duration.ofMinutes(15),
                clock
        );
    }

    @Test
    void blocksUserAfterRepeatedFailures() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("1.1.1." + i, "21012345");
        }

        assertThatThrownBy(() -> limiter.acquire("2.2.2.2", "21012345"))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .satisfies(e -> assertThat(((LoginRateLimitExceededException) e).retryAfterSeconds())
                        .isEqualTo(15 * 60));
    }

    @Test
    void userKeyIgnoresSurroundingWhitespaceAndCase() {
        limiter.acquire("1.1.1.1", "ab123");
        limiter.acquire("1.1.1.2", " AB123 ");
        limiter.acquire("1.1.1.3", "Ab123");

        assertThatThrownBy(() -> limiter.acquire("1.1.1.4", "ab123"))
                .isInstanceOf(LoginRateLimitExceededException.class);
    }

    @Test
    void unblocksUserAfterWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("1.1.1." + i, "21012345");
        }
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.acquire("1.1.1.1", "21012345")).doesNotThrowAnyException();
    }

    @Test
    void successResetsUserFailures() {
        limiter.acquire("1.1.1.1", "21012345");
        limiter.acquire("1.1.1.1", "21012345");
        limiter.acquire("1.1.1.1", "21012345");
        limiter.recordSuccess("1.1.1.1", "21012345");

        assertThatCode(() -> limiter.acquire("1.1.1.1", "21012345")).doesNotThrowAnyException();
    }

    @Test
    void inconclusiveAttemptDoesNotCountAsFailure() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("1.1.1.1", "21012345");
            limiter.recordInconclusive("1.1.1.1", "21012345");
        }

        assertThatCode(() -> limiter.acquire("1.1.1.1", "21012345")).doesNotThrowAnyException();
    }

    @Test
    void blocksIpTryingManyStudentIds() {
        for (int i = 0; i < 4; i++) {
            limiter.acquire("9.9.9.9", "2101000" + i);
        }

        assertThatThrownBy(() -> limiter.acquire("9.9.9.9", "21019999"))
                .isInstanceOf(LoginRateLimitExceededException.class);
        assertThatCode(() -> limiter.acquire("8.8.8.8", "21019999")).doesNotThrowAnyException();
    }

    @Test
    void userBlockDoesNotConsumeIpFailureSlot() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("1.1.1." + i, "21012345");
        }
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> limiter.acquire("9.9.9.9", "21012345"))
                    .isInstanceOf(LoginRateLimitExceededException.class);
        }

        assertThatCode(() -> limiter.acquire("9.9.9.9", "21010000")).doesNotThrowAnyException();
    }

    @Test
    void limitsTotalRequestsPerIpEvenWhenLoginsSucceed() {
        for (int i = 0; i < 10; i++) {
            limiter.acquire("7.7.7.7", "2101000" + i);
            limiter.recordSuccess("7.7.7.7", "2101000" + i);
        }

        assertThatThrownBy(() -> limiter.acquire("7.7.7.7", "21019999"))
                .isInstanceOf(LoginRateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire("7.7.7.7", "21019999")).doesNotThrowAnyException();
    }

    @Test
    void purgeRemovesExpiredWindows() {
        FixedWindowCounter counter = new FixedWindowCounter(1, Duration.ofMinutes(1), clock);
        counter.tryAcquire("a");
        counter.tryAcquire("b");
        clock.advance(Duration.ofMinutes(1));

        counter.purgeExpired();

        assertThat(counter.trackedKeyCount()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
