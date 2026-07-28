package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

@Service
public class SejongAuthService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(700);
    private static final String[] TLS_PROTOCOLS = {"TLSv1.2"};
    static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String PORTAL_HOME_URL = "https://portal.sejong.ac.kr/";
    private static final String PORTAL_LOGIN_URL =
            "https://portal.sejong.ac.kr/jsp/login/login_action.jsp";
    private static final String SSO_URL =
            "https://classic.sejong.ac.kr/_custom/sejong/sso/sso-return.jsp?returnUrl=https://classic.sejong.ac.kr/classic/index.do";

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
        BasicCookieStore cookieStore = new BasicCookieStore();

        try {
            primePortalSession(cookieStore);
            executeLogin(cookieStore, loginRequestDto);

            if (!hasSsoToken(cookieStore)) {
                throw new IllegalArgumentException("학번 또는 비밀번호가 틀렸습니다.");
            }

            executeSso(cookieStore);
            return new SejongSession(createHttpClient(cookieStore), cookieStore);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("세종 로그인 처리 중 오류가 발생했습니다.", e);
        }
    }

    private void primePortalSession(BasicCookieStore cookieStore)
            throws IOException, GeneralSecurityException {
        try (CloseableHttpClient httpClient = createHttpClient(cookieStore)) {
            HttpGet portalHomeRequest = new HttpGet(PORTAL_HOME_URL);
            portalHomeRequest.setConfig(createRequestConfig());
            portalHomeRequest.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            portalHomeRequest.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            portalHomeRequest.setHeader("Upgrade-Insecure-Requests", "1");
            portalHomeRequest.setHeader("User-Agent", DEFAULT_USER_AGENT);

            executeAndDrain(httpClient, portalHomeRequest);
        }
    }

    private void executeLogin(BasicCookieStore cookieStore, SejongLoginRequestDto loginRequestDto)
            throws IOException, GeneralSecurityException {
        try (CloseableHttpClient httpClient = createHttpClient(cookieStore)) {
            HttpPost loginRequest = new HttpPost(PORTAL_LOGIN_URL);
            loginRequest.setConfig(createRequestConfig());
            loginRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
            loginRequest.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            loginRequest.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            loginRequest.setHeader("Cache-Control", "max-age=0");
            loginRequest.setHeader("Referer", PORTAL_HOME_URL);
            loginRequest.setHeader("Origin", "https://portal.sejong.ac.kr");
            loginRequest.setHeader("Upgrade-Insecure-Requests", "1");
            loginRequest.setHeader("User-Agent", DEFAULT_USER_AGENT);
            loginRequest.setEntity(new UrlEncodedFormEntity(buildFormData(loginRequestDto), StandardCharsets.UTF_8));

            executeAndDrain(httpClient, loginRequest);
        }
    }

    private void executeSso(BasicCookieStore cookieStore)
            throws IOException, GeneralSecurityException {
        try (CloseableHttpClient httpClient = createHttpClient(cookieStore)) {
            HttpGet ssoRequest = new HttpGet(SSO_URL);
            ssoRequest.setConfig(createRequestConfig());
            ssoRequest.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            ssoRequest.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            ssoRequest.setHeader("Referer", PORTAL_HOME_URL);
            ssoRequest.setHeader("Upgrade-Insecure-Requests", "1");
            ssoRequest.setHeader("User-Agent", DEFAULT_USER_AGENT);

            executeAndDrain(httpClient, ssoRequest);
        }
    }

    private List<NameValuePair> buildFormData(SejongLoginRequestDto loginRequestDto) {
        return List.of(
                new BasicNameValuePair("mainLogin", "N"),
                new BasicNameValuePair("rtUrl", "library.sejong.ac.kr"),
                new BasicNameValuePair("id", loginRequestDto.getUserId()),
                new BasicNameValuePair("password", loginRequestDto.getPassword())
        );
    }

    private boolean hasSsoToken(BasicCookieStore cookieStore) {
        return cookieStore.getCookies().stream()
                .anyMatch(cookie -> "ssotoken".equalsIgnoreCase(cookie.getName()));
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

    private CloseableHttpClient createHttpClient(BasicCookieStore cookieStore)
            throws GeneralSecurityException {
        SSLContext sslContext = createTls12Context();

        return HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext))
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setSupportedProtocols(TLS_PROTOCOLS)
                                .build())
                        .build())
                .setDefaultRequestConfig(createRequestConfig())
                .disableAutomaticRetries()
                .build();
    }

    private RequestConfig createRequestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT.toMillis()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(REQUEST_TIMEOUT.toMillis()))
                .build();
    }

    private void executeAndDrain(CloseableHttpClient httpClient, HttpGet request) throws IOException {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    private void executeAndDrain(CloseableHttpClient httpClient, HttpPost request) throws IOException {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    private SSLContext createTls12Context() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, new SecureRandom());
        return sslContext;
    }
}
