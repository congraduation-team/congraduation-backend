package com.example.congraduation.service.sejong;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class SejongCurlSupport {

    static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final Duration CURL_TIMEOUT = Duration.ofSeconds(8);

    private SejongCurlSupport() {
    }

    static List<String> baseCurlCommand(Path cookieJarPath) {
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--http1.1");
        command.add("--silent");
        command.add("--show-error");
        command.add("--cookie");
        command.add(cookieJarPath.toString());
        command.add("--cookie-jar");
        command.add(cookieJarPath.toString());
        command.add("--user-agent");
        command.add(DEFAULT_USER_AGENT);
        command.add("--connect-timeout");
        command.add("10");
        command.add("--max-time");
        command.add(Long.toString(CURL_TIMEOUT.toSeconds()));
        return command;
    }

    static CurlExchange executeWithHeaders(
            List<String> command,
            Path headerFilePath,
            String actionDescription
    ) {
        List<String> commandWithHeaders = new ArrayList<>(command);
        commandWithHeaders.add("--dump-header");
        commandWithHeaders.add(headerFilePath.toString());

        String body = execute(commandWithHeaders, actionDescription);
        List<String> headers;
        try {
            headers = Files.readAllLines(headerFilePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(actionDescription + " 응답 헤더를 읽는 중 오류가 발생했습니다.", e);
        } finally {
            deleteIfExists(headerFilePath);
        }

        return new CurlExchange(body, headers, findHeader(headers, "Location"));
    }

    static String execute(List<String> command, String actionDescription) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(CURL_TIMEOUT.toSeconds() + 2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(actionDescription + " 중 curl 응답 시간이 초과되었습니다.");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new RuntimeException(actionDescription + " 중 curl 실행이 실패했습니다. exit code="
                        + process.exitValue() + ", output=" + truncate(output));
            }

            return output;
        } catch (IOException e) {
            throw new RuntimeException(actionDescription + " 중 curl 실행에 실패했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(actionDescription + " 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    static boolean cookieJarContains(Path cookieJarPath, String token) {
        try {
            return Files.readString(cookieJarPath).contains(token);
        } catch (IOException e) {
            throw new RuntimeException("세종 쿠키 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    static void deleteIfExists(Path filePath) {
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }

    static String resolveRedirectUrl(String currentUrl, String locationHeader) {
        if (locationHeader == null || locationHeader.isBlank()) {
            return null;
        }
        if (locationHeader.startsWith("http://") || locationHeader.startsWith("https://")) {
            return locationHeader;
        }
        if (locationHeader.startsWith("/")) {
            int schemeIndex = currentUrl.indexOf("://");
            int pathStart = currentUrl.indexOf('/', schemeIndex + 3);
            return (pathStart >= 0 ? currentUrl.substring(0, pathStart) : currentUrl) + locationHeader;
        }
        int lastSlash = currentUrl.lastIndexOf('/');
        return (lastSlash >= 0 ? currentUrl.substring(0, lastSlash + 1) : currentUrl + "/") + locationHeader;
    }

    private static String findHeader(List<String> headers, String headerName) {
        String prefix = headerName.toLowerCase(Locale.ROOT) + ":";
        for (String line : headers) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (normalized.startsWith(prefix)) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
    }

    private static String truncate(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300);
    }

    record CurlExchange(
            String body,
            List<String> headers,
            String location
    ) {
    }
}
