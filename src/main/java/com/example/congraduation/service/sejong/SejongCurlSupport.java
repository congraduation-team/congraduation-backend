package com.example.congraduation.service.sejong;

import java.io.IOException;
import java.io.InputStream;
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
        command.add("--compressed");
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

    static void executeDiscardBody(List<String> command, String actionDescription) {
        List<String> discardBodyCommand = new ArrayList<>(command);
        discardBodyCommand.add("--output");
        discardBodyCommand.add("/dev/null");
        execute(discardBodyCommand, actionDescription);
    }

    static String execute(List<String> command, String actionDescription) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            byte[] outputBytes = readAllBytesAsync(process.getInputStream(), actionDescription);
            boolean finished = process.waitFor(CURL_TIMEOUT.toSeconds() + 2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(actionDescription + " 중 curl 응답 시간이 초과되었습니다.");
            }

            String output = new String(outputBytes, StandardCharsets.UTF_8);
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

    private static byte[] readAllBytesAsync(InputStream inputStream, String actionDescription) {
        final byte[][] outputHolder = new byte[1][];
        final RuntimeException[] errorHolder = new RuntimeException[1];
        Thread readerThread = new Thread(() -> {
            try {
                outputHolder[0] = inputStream.readAllBytes();
            } catch (IOException e) {
                errorHolder[0] = new RuntimeException(actionDescription + " 응답 본문을 읽는 중 오류가 발생했습니다.", e);
            }
        }, "sejong-curl-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        try {
            readerThread.join(CURL_TIMEOUT.toMillis() + 2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(actionDescription + " 응답 본문 대기 중 인터럽트가 발생했습니다.", e);
        }

        if (readerThread.isAlive()) {
            throw new RuntimeException(actionDescription + " 응답 본문 읽기 시간이 초과되었습니다.");
        }
        if (errorHolder[0] != null) {
            throw errorHolder[0];
        }
        return outputHolder[0] == null ? new byte[0] : outputHolder[0];
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
