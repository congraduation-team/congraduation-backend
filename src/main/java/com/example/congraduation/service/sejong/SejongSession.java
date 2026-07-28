package com.example.congraduation.service.sejong;

import java.nio.file.Path;

public record SejongSession(Path cookieJarPath) {

    public void cleanup() {
        SejongCurlSupport.deleteIfExists(cookieJarPath);
    }
}
