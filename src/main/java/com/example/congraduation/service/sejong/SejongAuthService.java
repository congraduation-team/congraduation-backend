package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class SejongAuthService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(700);
    static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String PORTAL_LOGIN_URL =
            "https://portal.sejong.ac.kr/jsp/login/login_action.jsp";
    private static final String PORTAL_HOME_URL = "https://portal.sejong.ac.kr/";
    private static final String SSO_URL =
            "https://classic.sejong.ac.kr/_custom/sejong/sso/sso-return.jsp?returnUrl=https://classic.sejong.ac.kr/classic/index.do";
    private static final String CLASSIC_INDEX_URL = "https://classic.sejong.ac.kr/classic/index.do";

    public SejongSession login(SejongLoginRequestDto loginRequestDto) {
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return authenticate(loginRequestDto);
            } catch (RuntimeException e) {
                lastException = e;
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
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            sendPortalWarmup(cookieManager);

            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PORTAL_LOGIN_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", "https://portal.sejong.ac.kr/")
                    .header("Origin", "https://portal.sejong.ac.kr")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormData(loginRequestDto)))
                    .build();

            HttpResponse<String> loginResponse = createHttpClient(cookieManager)
                    .send(loginRequest, HttpResponse.BodyHandlers.ofString());

            if (!hasSsoToken(cookieManager) || !isLoginSuccess(loginResponse.body())) {
                throw new IllegalArgumentException("학번 또는 비밀번호가 틀렸습니다.");
            }

            HttpRequest ssoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SSO_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Referer", "https://portal.sejong.ac.kr/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> ssoResponse = createHttpClient(cookieManager)
                    .send(ssoRequest, HttpResponse.BodyHandlers.ofString());

            followClassicRedirect(cookieManager, ssoResponse);

            return new SejongSession(cookieManager);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("세종 로그인 처리 중 오류가 발생했습니다.", e);
        }
    }

    private void sendPortalWarmup(CookieManager cookieManager) throws IOException, InterruptedException {
        HttpRequest warmupRequest = HttpRequest.newBuilder()
                .uri(URI.create(PORTAL_HOME_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET()
                .build();

        createHttpClient(cookieManager).send(warmupRequest, HttpResponse.BodyHandlers.discarding());
    }

    private void followClassicRedirect(
            CookieManager cookieManager,
            HttpResponse<String> ssoResponse
    ) throws IOException, InterruptedException {
        URI targetUri = resolveRedirectTarget(ssoResponse);

        HttpRequest classicIndexRequest = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Referer", "https://portal.sejong.ac.kr/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET()
                .build();

        createHttpClient(cookieManager).send(classicIndexRequest, HttpResponse.BodyHandlers.discarding());
    }

    private URI resolveRedirectTarget(HttpResponse<String> ssoResponse) {
        String location = ssoResponse.headers().firstValue("Location").orElse(null);
        if (location == null || location.isBlank()) {
            return URI.create(CLASSIC_INDEX_URL);
        }

        try {
            URI locationUri = new URI(location);
            if (locationUri.isAbsolute()) {
                return locationUri;
            }
            return URI.create("https://classic.sejong.ac.kr").resolve(locationUri);
        } catch (URISyntaxException e) {
            return URI.create(CLASSIC_INDEX_URL);
        }
    }

    private HttpClient createHttpClient(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private String buildFormData(SejongLoginRequestDto loginRequestDto) {
        return "mainLogin=" + encode("N")
                + "&rtUrl=" + encode("library.sejong.ac.kr")
                + "&id=" + encode(loginRequestDto.getUserId())
                + "&password=" + encode(loginRequestDto.getPassword());
    }

    private boolean hasSsoToken(CookieManager cookieManager) {
        return cookieManager.getCookieStore().getCookies().stream()
                .map(HttpCookie::getName)
                .anyMatch("ssotoken"::equalsIgnoreCase);
    }

    private boolean isLoginSuccess(String body) {
        return body != null && body.contains("var result = 'OK'");
    }

    private boolean isRetryable(RuntimeException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof SocketException || cause instanceof SocketTimeoutException;
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
