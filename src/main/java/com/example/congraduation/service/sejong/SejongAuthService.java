package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SejongAuthService {

    private static final Logger log = LoggerFactory.getLogger(SejongAuthService.class);
    private static final int MAX_ATTEMPTS = 1;
    private static final Duration RETRY_DELAY = Duration.ofMillis(300);
    private static final String PORTAL_LOGIN_URL =
            "https://portal.sejong.ac.kr/jsp/login/login_action.jsp";
    private static final String PORTAL_HOME_URL = "https://portal.sejong.ac.kr/";
    private static final String SSO_URL =
            "https://classic.sejong.ac.kr/_custom/sejong/sso/sso-return.jsp?returnUrl=https://classic.sejong.ac.kr/classic/index.do";
    private static final String CLASSIC_INDEX_URL = "https://classic.sejong.ac.kr/classic/index.do";
    private static final int MAX_SSO_REDIRECTS = 5;

    public SejongSession login(SejongLoginRequestDto loginRequestDto) {
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Starting Sejong login attempt {}", attempt);
                return authenticate(loginRequestDto);
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("Sejong login attempt {} failed: {}", attempt, e.getMessage());
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                sleepBeforeRetry();
            }
        }

        throw lastException == null
                ? new RuntimeException("세종 로그인 처리 중 오류가 발생했습니다.")
                : lastException;
    }

    private SejongSession authenticate(SejongLoginRequestDto loginRequestDto) {
        Path cookieJarPath = null;
        try {
            cookieJarPath = Files.createTempFile("sejong-cookies-", ".txt");
            log.info("Created Sejong cookie jar at {}", cookieJarPath);

            sendPortalWarmup(cookieJarPath);

            String loginResponse = submitPortalLogin(cookieJarPath, loginRequestDto);

            if (!hasSsoToken(cookieJarPath) || !isLoginSuccess(loginResponse)) {
                throw new IllegalArgumentException("학번 또는 비밀번호가 틀렸습니다.");
            }

            followSso(cookieJarPath);
            log.info("Completed Sejong SSO flow");

            return new SejongSession(cookieJarPath);
        } catch (IOException e) {
            SejongCurlSupport.deleteIfExists(cookieJarPath);
            throw new RuntimeException("세종 로그인 처리 중 오류가 발생했습니다.", e);
        } catch (RuntimeException e) {
            SejongCurlSupport.deleteIfExists(cookieJarPath);
            throw e;
        }
    }

    private void sendPortalWarmup(Path cookieJarPath) {
        log.info("Running Sejong portal warmup");
        List<String> command = SejongCurlSupport.baseCurlCommand(cookieJarPath);
        command.add("--header");
        command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        command.add("--header");
        command.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        command.add(PORTAL_HOME_URL);
        SejongCurlSupport.execute(command, "세종 포털 워밍업");
    }

    private String submitPortalLogin(Path cookieJarPath, SejongLoginRequestDto loginRequestDto) {
        log.info("Submitting Sejong portal login request");
        List<String> command = SejongCurlSupport.baseCurlCommand(cookieJarPath);
        command.add("--request");
        command.add("POST");
        command.add("--header");
        command.add("Content-Type: application/x-www-form-urlencoded");
        command.add("--header");
        command.add("Referer: https://portal.sejong.ac.kr/");
        command.add("--header");
        command.add("Origin: https://portal.sejong.ac.kr");
        command.add("--header");
        command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        command.add("--header");
        command.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        command.add("--data-raw");
        command.add(buildFormData(loginRequestDto));
        command.add(PORTAL_LOGIN_URL);
        return SejongCurlSupport.execute(command, "세종 포털 로그인");
    }

    private void followSso(Path cookieJarPath) {
        String currentUrl = SSO_URL;
        String referer = PORTAL_HOME_URL;

        for (int redirectCount = 0; redirectCount < MAX_SSO_REDIRECTS; redirectCount++) {
            Path headerFilePath;
            try {
                headerFilePath = Files.createTempFile("sejong-sso-headers-", ".txt");
            } catch (IOException e) {
                throw new RuntimeException("세종 SSO 헤더 파일 생성에 실패했습니다.", e);
            }

            List<String> command = SejongCurlSupport.baseCurlCommand(cookieJarPath);
            command.add("--header");
            command.add("Referer: " + referer);
            command.add("--header");
            command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            command.add("--header");
            command.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            command.add(currentUrl);

            log.info("Sejong SSO step {} requesting {}", redirectCount + 1, currentUrl);

            SejongCurlSupport.CurlExchange exchange = SejongCurlSupport.executeWithHeaders(
                    command,
                    headerFilePath,
                    "세종 SSO 연동"
            );

            String nextLocation = SejongCurlSupport.resolveRedirectUrl(currentUrl, exchange.location());
            log.info(
                    "Sejong SSO step {} location header: {}, resolved next: {}",
                    redirectCount + 1,
                    exchange.location(),
                    nextLocation
            );
            if (nextLocation == null || nextLocation.isBlank()) {
                if (exchange.body() != null && exchange.body().contains("/classic/index.do")) {
                    log.info("Sejong SSO step {} finished with classic index marker in body", redirectCount + 1);
                    return;
                }
                log.info("Sejong SSO step {} finished without redirect", redirectCount + 1);
                return;
            }

            referer = currentUrl;
            currentUrl = nextLocation;
        }

        log.warn("Sejong SSO exceeded redirect limit, trying classic index fallback");
        List<String> fallbackCommand = SejongCurlSupport.baseCurlCommand(cookieJarPath);
        fallbackCommand.add("--header");
        fallbackCommand.add("Referer: " + PORTAL_HOME_URL);
        fallbackCommand.add(CLASSIC_INDEX_URL);
        SejongCurlSupport.execute(fallbackCommand, "세종 classic 인덱스 진입");
    }

    private String buildFormData(SejongLoginRequestDto loginRequestDto) {
        return "mainLogin=" + encode("N")
                + "&rtUrl=" + encode("library.sejong.ac.kr")
                + "&id=" + encode(loginRequestDto.getUserId())
                + "&password=" + encode(loginRequestDto.getPassword());
    }

    private boolean hasSsoToken(Path cookieJarPath) {
        return SejongCurlSupport.cookieJarContains(cookieJarPath, "ssotoken");
    }

    private boolean isLoginSuccess(String body) {
        return body != null && body.contains("var result = 'OK'");
    }

    private boolean isRetryable(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("timed out")
                || message.contains("curl 실행이 실패")
                || message.contains("응답 시간"));
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("세종 로그인 재시도 대기 중 인터럽트가 발생했습니다.", interruptedException);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
