package com.example.congraduation.logging;

/**
 * 로그에 남기는 값에서 개인정보·토큰을 가린다.
 */
public final class LogMasking {

    private static final int VISIBLE_STUDENT_ID_PREFIX = 4;

    private LogMasking() {
    }

    /** 학번은 앞 4자리(입학연도)만 남긴다. 예: 21012345 → 2101**** */
    public static String maskStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return "(empty)";
        }
        String trimmed = studentId.trim();
        if (trimmed.length() <= VISIBLE_STUDENT_ID_PREFIX) {
            return "****";
        }
        return trimmed.substring(0, VISIBLE_STUDENT_ID_PREFIX) + "****";
    }

    /**
     * URL의 쿼리스트링·프래그먼트를 가린다. SSO 리다이렉트 URL에는 토큰이 실려 올 수 있다.
     * 예: https://a.com/sso?token=abc → https://a.com/sso?[redacted]
     */
    public static String redactUrl(String url) {
        if (url == null) {
            return null;
        }
        int cut = indexOfFirst(url, '?', '#');
        if (cut < 0) {
            return url;
        }
        return url.substring(0, cut) + "?[redacted]";
    }

    private static int indexOfFirst(String value, char first, char second) {
        int a = value.indexOf(first);
        int b = value.indexOf(second);
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }
}
