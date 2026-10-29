package com.example.congraduation.auth;

import com.example.congraduation.logging.LogMasking;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 세종 포털 비밀번호를 그대로 중계하는 로그인 API의 반복 시도를 막는다.
 *
 * <ul>
 *   <li>IP별 전체 요청 수: 짧은 시간의 폭주(포털·curl 프로세스 부하) 차단</li>
 *   <li>IP별 실패 수: 한 IP에서 여러 학번을 돌려 보는 크리덴셜 스터핑 차단</li>
 *   <li>학번별 실패 수: 한 계정에 대한 비밀번호 대입 차단(포털 계정 잠금 유발도 방지)</li>
 * </ul>
 *
 * 실패 카운터는 {@link #acquire}에서 미리 센다(동시 요청으로 한도를 우회하지 못하게).
 * 학번/비밀번호 불일치면 그대로 실패로 남고, 성공하면 학번 카운터를 초기화하며,
 * 포털 장애처럼 판정이 안 난 시도는 차감한다.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final FixedWindowCounter ipRequests;
    private final FixedWindowCounter ipFailures;
    private final FixedWindowCounter userFailures;

    @Autowired
    public LoginRateLimiter(
            @Value("${app.login-rate-limit.ip-requests.max:20}") int ipRequestLimit,
            @Value("${app.login-rate-limit.ip-requests.window:1m}") Duration ipRequestWindow,
            @Value("${app.login-rate-limit.ip-failures.max:20}") int ipFailureLimit,
            @Value("${app.login-rate-limit.ip-failures.window:15m}") Duration ipFailureWindow,
            @Value("${app.login-rate-limit.user-failures.max:5}") int userFailureLimit,
            @Value("${app.login-rate-limit.user-failures.window:15m}") Duration userFailureWindow
    ) {
        this(ipRequestLimit, ipRequestWindow, ipFailureLimit, ipFailureWindow,
                userFailureLimit, userFailureWindow, Clock.systemUTC());
    }

    LoginRateLimiter(
            int ipRequestLimit,
            Duration ipRequestWindow,
            int ipFailureLimit,
            Duration ipFailureWindow,
            int userFailureLimit,
            Duration userFailureWindow,
            Clock clock
    ) {
        this.ipRequests = new FixedWindowCounter(ipRequestLimit, ipRequestWindow, clock);
        this.ipFailures = new FixedWindowCounter(ipFailureLimit, ipFailureWindow, clock);
        this.userFailures = new FixedWindowCounter(userFailureLimit, userFailureWindow, clock);
    }

    /**
     * 로그인 시도 슬롯을 확보한다. 한도를 넘으면 아무 카운터도 남기지 않고(IP 요청 수 제외) 예외를 던진다.
     */
    public void acquire(String clientIp, String userId) {
        String ipKey = normalizeIp(clientIp);
        String userKey = normalizeUserId(userId);

        Optional<Duration> rejected = ipRequests.tryAcquire(ipKey);
        if (rejected.isPresent()) {
            throw reject("ip-requests", ipKey, userKey, rejected.get());
        }
        rejected = ipFailures.tryAcquire(ipKey);
        if (rejected.isPresent()) {
            throw reject("ip-failures", ipKey, userKey, rejected.get());
        }
        rejected = userFailures.tryAcquire(userKey);
        if (rejected.isPresent()) {
            ipFailures.release(ipKey);
            throw reject("user-failures", ipKey, userKey, rejected.get());
        }
    }

    /** 인증 성공: 이 시도가 차지한 IP 실패 슬롯을 돌려주고 학번 실패 이력은 지운다. */
    public void recordSuccess(String clientIp, String userId) {
        ipFailures.release(normalizeIp(clientIp));
        userFailures.reset(normalizeUserId(userId));
    }

    /** 포털 장애 등으로 자격 증명을 판정하지 못함: 사용자에게 불이익이 없도록 슬롯을 돌려준다. */
    public void recordInconclusive(String clientIp, String userId) {
        ipFailures.release(normalizeIp(clientIp));
        userFailures.release(normalizeUserId(userId));
    }

    private LoginRateLimitExceededException reject(String rule, String ipKey, String userKey, Duration retryAfter) {
        log.warn("Login rate limit exceeded: rule={}, ip={}, userId={}, retryAfterSeconds={}",
                rule, ipKey, LogMasking.maskStudentId(userKey), retryAfter.toSeconds());
        return new LoginRateLimitExceededException(retryAfter);
    }

    private static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private static String normalizeUserId(String userId) {
        return userId == null ? "" : userId.trim().toLowerCase(Locale.ROOT);
    }
}
