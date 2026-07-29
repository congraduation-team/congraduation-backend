package com.example.congraduation.service.transcript;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.MajorCreditSummaryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MajorCreditSummaryService {

    public MajorCreditSummaryDto summarize(List<CompletedCourseUploadRowDto> courses) {
        BigDecimal requiredCredits = BigDecimal.ZERO;
        BigDecimal electiveCredits = BigDecimal.ZERO;
        int requiredCount = 0;
        int electiveCount = 0;

        for (CompletedCourseUploadRowDto course : courses) {
            if (!isPassed(course)) {
                continue;
            }

            MajorBucket bucket = classify(course.category());
            if (bucket == MajorBucket.NONE) {
                continue;
            }

            BigDecimal credit = toDecimal(course.credit());
            if (bucket == MajorBucket.REQUIRED) {
                requiredCredits = requiredCredits.add(credit);
                requiredCount++;
            } else {
                electiveCredits = electiveCredits.add(credit);
                electiveCount++;
            }
        }

        double required = toDouble(requiredCredits);
        double elective = toDouble(electiveCredits);

        return new MajorCreditSummaryDto(
                required,
                elective,
                toDouble(requiredCredits.add(electiveCredits)),
                requiredCount,
                electiveCount,
                courses.size()
        );
    }

    private MajorBucket classify(String category) {
        if (category == null || category.isBlank()) {
            return MajorBucket.NONE;
        }

        String value = category.replace(" ", "").trim();

        // 전필 / 전공필수
        if (value.contains("전필") || value.contains("전공필수")) {
            return MajorBucket.REQUIRED;
        }

        // 전선 / 전공선택 (전공기초는 전선으로 치지 않음)
        if (value.contains("전선") || value.contains("전공선택")) {
            return MajorBucket.ELECTIVE;
        }

        return MajorBucket.NONE;
    }

    private boolean isPassed(CompletedCourseUploadRowDto course) {
        String grade = course.grade();
        if (grade == null || grade.isBlank()) {
            return true;
        }
        String normalized = grade.trim().toUpperCase(Locale.ROOT);
        return !(normalized.equals("F")
                || normalized.equals("NP")
                || normalized.equals("N")
                || normalized.equals("U")
                || normalized.equals("FA"));
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private double toDouble(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().doubleValue();
    }

    private enum MajorBucket {
        REQUIRED,
        ELECTIVE,
        NONE
    }
}
