package com.example.congraduation.service.sejong;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public record SejongSession(
        CloseableHttpClient httpClient,
        BasicCookieStore cookieStore
) {
}
