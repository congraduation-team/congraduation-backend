package com.example.congraduation.service.sejong;

import java.net.CookieManager;
import java.net.http.HttpClient;

public record SejongSession(
        HttpClient httpClient,
        CookieManager cookieManager
) {
}
