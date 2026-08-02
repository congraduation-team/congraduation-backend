package com.example.congraduation.service.transcript;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.TranscriptSummaryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TranscriptSummaryCalculator {

    public TranscriptSummaryDto summarize(List<CompletedCourseUploadRowDto> courses) {
        List<CompletedCourseUploadRowDto> resolvedCourses = resolveRetakenCourses(courses);
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalGradePoints = BigDecimal.ZERO;
        Map<String, CategoryAccumulator> categoryMap = new LinkedHashMap<>();

        for (CompletedCourseUploadRowDto course : resolvedCourses) {
            BigDecimal credit = toDecimal(course.credit());
            String category = normalizeCategory(course.category());

            if (isPassedForCompletion(course)) {
                totalCredits = totalCredits.add(credit);

                CategoryAccumulator accumulator = categoryMap.computeIfAbsent(
                        category,
                        key -> new CategoryAccumulator()
                );
                accumulator.earnedCredits = accumulator.earnedCredits.add(credit);
                accumulator.courses.add(new CategoryCourseDto(
                        course.courseCode(),
                        course.courseName(),
                        course.credit()
                ));
            }

            if (isCountableForGpa(course)) {
                BigDecimal gradePoint = toDecimal(course.gradePoint());
                totalGradePoints = totalGradePoints.add(credit.multiply(gradePoint));
            }
        }

        BigDecimal averageGradePoint = BigDecimal.ZERO;
        BigDecimal gpaCredits = resolvedCourses.stream()
                .filter(this::isCountableForGpa)
                .map(course -> toDecimal(course.credit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (gpaCredits.compareTo(BigDecimal.ZERO) > 0) {
            averageGradePoint = totalGradePoints.divide(gpaCredits, 2, RoundingMode.HALF_UP);
        }

        List<CategorySummaryDto> categorySummaries = new ArrayList<>();
        for (Map.Entry<String, CategoryAccumulator> entry : categoryMap.entrySet()) {
            categorySummaries.add(new CategorySummaryDto(
                    entry.getKey(),
                    formatDecimal(entry.getValue().earnedCredits),
                    null,
                    false,
                    null,
                    entry.getValue().courses
            ));
        }

        return new TranscriptSummaryDto(
                formatDecimal(totalCredits),
                formatDecimal(totalGradePoints),
                formatDecimal(averageGradePoint),
                categorySummaries
        );
    }

    public List<CompletedCourseUploadRowDto> resolveRetakenCourses(List<CompletedCourseUploadRowDto> courses) {
        Map<String, CompletedCourseUploadRowDto> resolved = new LinkedHashMap<>();
        List<CompletedCourseUploadRowDto> blankCodeCourses = new ArrayList<>();

        for (CompletedCourseUploadRowDto course : courses) {
            String courseCode = normalizeCourseCode(course.courseCode());
            if (courseCode.isBlank()) {
                blankCodeCourses.add(course);
                continue;
            }
            CompletedCourseUploadRowDto previous = resolved.remove(courseCode);
            resolved.put(courseCode, mergeRetakenCourse(previous, course));
        }

        List<CompletedCourseUploadRowDto> deduplicated = new ArrayList<>(blankCodeCourses);
        deduplicated.addAll(resolved.values());
        return deduplicated;
    }

    private CompletedCourseUploadRowDto mergeRetakenCourse(
            CompletedCourseUploadRowDto previous,
            CompletedCourseUploadRowDto replacement
    ) {
        if (previous == null) {
            return replacement;
        }

        return new CompletedCourseUploadRowDto(
                replacement.year(),
                replacement.semester(),
                replacement.courseCode(),
                replacement.courseName(),
                firstNonBlank(previous.category(), replacement.category()),
                replacement.credit(),
                replacement.evaluationMethod(),
                replacement.grade(),
                replacement.gradePoint(),
                firstNonBlank(previous.openingDepartmentCode(), replacement.openingDepartmentCode())
        );
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "미분류";
        }

        String normalized = category.trim();
        return switch (normalized) {
            case "교양", "교양선택" -> "교선";
            case "공통교양필수", "공통교양" -> "공필";
            case "균형교양" -> "균필";
            case "전공필수" -> "전필";
            case "전공선택" -> "전선";
            default -> normalized;
        };
    }

    private boolean isCountableForGpa(CompletedCourseUploadRowDto course) {
        String evaluationMethod = course.evaluationMethod();
        if (evaluationMethod != null && !evaluationMethod.isBlank()) {
            return "GRADE".equalsIgnoreCase(evaluationMethod.trim());
        }

        // Backward compatibility for rows uploaded before evaluationMethod was persisted.
        return toDecimal(course.gradePoint()).compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isPassedForCompletion(CompletedCourseUploadRowDto course) {
        String grade = normalizeGrade(course.grade());
        if ("F".equals(grade) || "FA".equals(grade) || "NP".equals(grade)) {
            return false;
        }

        String evaluationMethod = course.evaluationMethod();
        if (evaluationMethod != null
                && "P/NP".equalsIgnoreCase(evaluationMethod.trim())
                && (grade == null || grade.isBlank())) {
            return false;
        }

        return true;
    }

    private String normalizeGrade(String grade) {
        return grade == null ? null : grade.trim().toUpperCase();
    }

    private String normalizeCourseCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static class CategoryAccumulator {
        private BigDecimal earnedCredits = BigDecimal.ZERO;
        private final List<CategoryCourseDto> courses = new ArrayList<>();
    }
}
