package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.StudentMajorTrack;
import com.example.congraduation.dto.graduation.CreditProgressDto;
import com.example.congraduation.dto.graduation.MajorTrackProgressDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MinorTrackProgressService {

    private static final Set<String> MINOR_REQUIRED_CATEGORIES = Set.of("부필", "부전필", "연부필", "연계부필");
    private static final Set<String> MINOR_ELECTIVE_CATEGORIES = Set.of("부선", "부전선", "연부선", "연계부선");
    private static final String IOT_MAJOR = "지능IoT학과";
    private static final String IOT_MAJOR_ALT = "지능IOT학과";

    public MajorTrackProgressDto evaluate(StudentMajorTrack track, List<CategorySummaryDto> categorySummaries) {
        String department = normalizeDepartment(track.getDepartmentCode());
        int requiredCredits = "건축학과".equals(department) ? 51 : 21;

        BigDecimal required = creditOf(categorySummaries, MINOR_REQUIRED_CATEGORIES);
        BigDecimal elective = creditOf(categorySummaries, MINOR_ELECTIVE_CATEGORIES);
        BigDecimal total = required.add(elective);
        boolean creditSatisfied = total.compareTo(BigDecimal.valueOf(requiredCredits)) >= 0;

        String status = creditSatisfied ? "COMPLETED" : "IN_PROGRESS";
        String detail = department + " 부전공은 " + requiredCredits + "학점 이상 이수해야 합니다.";

        if (isIotMajor(department)) {
            detail = "지능IoT학과 부전공은 " + requiredCredits + "학점 외에 공유형 또는 교류형 마이크로디그리 요건 확인이 필요합니다.";
            if (creditSatisfied) {
                status = "MANUAL_CHECK_REQUIRED";
            }
        }

        return new MajorTrackProgressDto(
                track.getTrackType(),
                department,
                new CreditProgressDto(
                        formatDecimal(total),
                        String.valueOf(requiredCredits),
                        creditSatisfied,
                        toPercentString(total, requiredCredits)
                ),
                null,
                null,
                null,
                null,
                "부필/부선",
                status,
                detail
        );
    }

    private boolean isIotMajor(String department) {
        return IOT_MAJOR.equals(department) || IOT_MAJOR_ALT.equals(department);
    }

    private BigDecimal creditOf(List<CategorySummaryDto> categorySummaries, Set<String> categories) {
        return categorySummaries.stream()
                .filter(summary -> categories.contains(summary.category()))
                .map(summary -> toDecimal(summary.earnedCredits()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String toPercentString(BigDecimal earned, int required) {
        if (required <= 0) {
            return null;
        }
        BigDecimal percent = earned.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(required), 0, java.math.RoundingMode.HALF_UP);
        return percent.toPlainString();
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String normalizeDepartment(String department) {
        return department == null ? "" : department.trim();
    }
}
