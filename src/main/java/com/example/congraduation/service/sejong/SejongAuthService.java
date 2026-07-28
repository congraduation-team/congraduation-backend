package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.net.SocketException;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.springframework.stereotype.Service;

@Service
public class SejongAuthService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(700);
    private static final String[] TLS_PROTOCOLS = {"TLSv1.2"};
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String PORTAL_HOME_URL =
            "https://portal.sejong.ac.kr/";

    private static final String PORTAL_LOGIN_URL =
            "https://portal.sejong.ac.kr/jsp/login/login_action.jsp";

    private static final String SSO_URL =
            "http://classic.sejong.ac.kr/_custom/sejong/sso/sso-return.jsp?returnUrl=https://classic.sejong.ac.kr/classic/index.do";

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

            HttpClient httpClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .sslContext(createTls12Context())
                    .sslParameters(createTls12Parameters())
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            primePortalSession(httpClient);

            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PORTAL_LOGIN_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "max-age=0")
                    .header("Referer", PORTAL_HOME_URL)
                    .header("Origin", "https://portal.sejong.ac.kr")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormData(loginRequestDto)))
                    .build();

            httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());

            if (!hasSsoToken(cookieManager)) {
                throw new IllegalArgumentException("학번 또는 비밀번호가 틀렸습니다.");
            }

            HttpRequest ssoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SSO_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", PORTAL_HOME_URL)
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .GET()
                    .build();

            httpClient.send(ssoRequest, HttpResponse.BodyHandlers.ofString());
            return new SejongSession(httpClient, cookieManager);
        } catch (IOException | InterruptedException | GeneralSecurityException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("세종 로그인 처리 중 오류가 발생했습니다.", e);
        }
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

    private boolean isRetryable(RuntimeException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof SocketException || cause instanceof HttpTimeoutException;
    }

    private void primePortalSession(HttpClient httpClient) throws IOException, InterruptedException {
        HttpRequest portalHomeRequest = HttpRequest.newBuilder()
                .uri(URI.create(PORTAL_HOME_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Upgrade-Insecure-Requests", "1")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET()
                .build();

        httpClient.send(portalHomeRequest, HttpResponse.BodyHandlers.ofString());
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("세종 로그인 재시도 대기 중 인터럽트가 발생했습니다.", interruptedException);
        }
    }

    private SSLContext createTls12Context() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, new SecureRandom());
        return sslContext;
    }

    private SSLParameters createTls12Parameters() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(TLS_PROTOCOLS);
        return sslParameters;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
