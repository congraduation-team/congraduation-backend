package com.example.hackathon.service.transcript;

import com.example.hackathon.dto.transcript.CategoryCourseDto;
import com.example.hackathon.dto.transcript.CategorySummaryDto;
import com.example.hackathon.dto.transcript.CompletedCourseUploadRowDto;
import com.example.hackathon.dto.transcript.TranscriptSummaryDto;
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
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalGradePoints = BigDecimal.ZERO;
        Map<String, CategoryAccumulator> categoryMap = new LinkedHashMap<>();

        for (CompletedCourseUploadRowDto course : courses) {
            BigDecimal credit = toDecimal(course.credit());
            String category = normalizeCategory(course.category());

            totalCredits = totalCredits.add(credit);

            if (isCountableForGpa(course)) {
                BigDecimal gradePoint = toDecimal(course.gradePoint());
                totalGradePoints = totalGradePoints.add(credit.multiply(gradePoint));
            }

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

        BigDecimal averageGradePoint = BigDecimal.ZERO;
        BigDecimal gpaCredits = courses.stream()
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
        if ("교양".equals(normalized)) {
            return "교선";
        }

        return normalized;
    }

    private boolean isCountableForGpa(CompletedCourseUploadRowDto course) {
        String evaluationMethod = course.evaluationMethod();
        if (evaluationMethod != null && !evaluationMethod.isBlank()) {
            return "GRADE".equalsIgnoreCase(evaluationMethod.trim());
        }

        // Backward compatibility for rows uploaded before evaluationMethod was persisted.
        return toDecimal(course.gradePoint()).compareTo(BigDecimal.ZERO) > 0;
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static class CategoryAccumulator {
        private BigDecimal earnedCredits = BigDecimal.ZERO;
        private final List<CategoryCourseDto> courses = new ArrayList<>();
    }
}
