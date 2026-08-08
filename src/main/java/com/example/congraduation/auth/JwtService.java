package com.example.congraduation.auth;

import com.example.congraduation.domain.Student;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_TYPE = "Bearer";

    private final byte[] secretKeyBytes;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret:change-this-jwt-secret-for-production-minimum-32-bytes}") String secret,
            @Value("${app.jwt.expiration-seconds:43200}") long expirationSeconds
    ) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters.");
        }
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public JwtTokenDto issueToken(Student student) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        if (student.getId() == null) {
            throw new IllegalArgumentException("JWT 발급 대상 학생의 DB PK가 없습니다.");
        }
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = "{"
                + "\"sub\":" + student.getId() + ","
                + "\"studentNo\":\"" + escapeJson(student.getStudentNo()) + "\","
                + "\"admin\":" + student.isAdmin() + ","
                + "\"iat\":" + issuedAt.getEpochSecond() + ","
                + "\"exp\":" + expiresAt.getEpochSecond()
                + "}";
        String header = encode(headerJson);
        String payload = encode(payloadJson);
        String signature = sign(header + "." + payload);
        return new JwtTokenDto(
                header + "." + payload + "." + signature,
                TOKEN_TYPE,
                expiresAt.getEpochSecond()
        );
    }

    public AuthenticatedStudent parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtAuthenticationException("JWT 토큰이 비어 있습니다.");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtAuthenticationException("JWT 형식이 올바르지 않습니다.");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new JwtAuthenticationException("JWT 서명이 유효하지 않습니다.");
        }

        String payloadJson = decode(parts[1]);
        long expiration = extractLong(payloadJson, "exp");
        if (Instant.now().getEpochSecond() >= expiration) {
            throw new JwtAuthenticationException("JWT 토큰이 만료되었습니다.");
        }

        return new AuthenticatedStudent(
                extractLong(payloadJson, "sub"),
                extractString(payloadJson, "studentNo"),
                extractBoolean(payloadJson, "admin")
        );
    }

    public String getTokenType() {
        return TOKEN_TYPE;
    }

    public record JwtTokenDto(
            String accessToken,
            String tokenType,
            long expiresAt
    ) {
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
            return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명 생성에 실패했습니다.", e);
        }
    }

    private String encode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(URL_DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private long extractLong(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new JwtAuthenticationException("JWT payload에 " + key + " 값이 없습니다.");
        }
        int valueStart = start + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        return Long.parseLong(json.substring(valueStart, valueEnd));
    }

    private boolean extractBoolean(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new JwtAuthenticationException("JWT payload에 " + key + " 값이 없습니다.");
        }
        int valueStart = start + marker.length();
        if (json.startsWith("true", valueStart)) {
            return true;
        }
        if (json.startsWith("false", valueStart)) {
            return false;
        }
        throw new JwtAuthenticationException("JWT payload의 " + key + " 값이 올바르지 않습니다.");
    }

    private String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new JwtAuthenticationException("JWT payload에 " + key + " 값이 없습니다.");
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new JwtAuthenticationException("JWT payload의 " + key + " 값이 올바르지 않습니다.");
        }
        return json.substring(valueStart, valueEnd)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
