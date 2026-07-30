package com.example.congraduation.dto.plan;

import java.util.Map;

public final class PlannedCourseGradePolicy {

    private static final Map<String, String> GRADE_POINTS = Map.of(
            "A+", "4.5",
            "A0", "4.0",
            "B+", "3.5",
            "B0", "3.0",
            "C+", "2.5",
            "C0", "2.0",
            "D+", "1.5",
            "D0", "1.0",
            "F", "0"
    );

    private PlannedCourseGradePolicy() {
    }

    public static String normalize(String expectedGrade) {
        return expectedGrade == null ? null : expectedGrade.trim().toUpperCase();
    }

    public static boolean isSupported(String expectedGrade) {
        String normalized = normalize(expectedGrade);
        return normalized == null || normalized.isBlank()
                || GRADE_POINTS.containsKey(normalized)
                || "P".equals(normalized)
                || "NP".equals(normalized);
    }

    public static String toGradePoint(String expectedGrade) {
        String normalized = normalize(expectedGrade);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if ("P".equals(normalized) || "NP".equals(normalized)) {
            return "0";
        }
        return GRADE_POINTS.get(normalized);
    }

    public static String toEvaluationMethod(String expectedGrade) {
        String normalized = normalize(expectedGrade);
        if (normalized == null || normalized.isBlank()) {
            return "PLANNED";
        }
        if ("P".equals(normalized) || "NP".equals(normalized)) {
            return "P/NP";
        }
        return "GRADE";
    }
}
