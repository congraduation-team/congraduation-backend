package com.example.congraduation.service.graduation;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BalancedLiberalCoursePolicyService {

    private static final String AREA_HISTORY = "역사와사상";
    private static final String AREA_NATURE = "자연과과학";
    private static final String AREA_SOCIETY = "경제와사회";
    private static final String AREA_CULTURE = "문화와예술";
    private static final String AREA_FUSION = "융합과창의";

    private static final Map<String, String> BASE_AREA_BY_COURSE_CODE = Map.ofEntries(
            Map.entry("006937", AREA_HISTORY),
            Map.entry("011305", AREA_HISTORY),
            Map.entry("011306", AREA_HISTORY),
            Map.entry("011307", AREA_HISTORY),
            Map.entry("011308", AREA_NATURE),
            Map.entry("011309", AREA_NATURE),
            Map.entry("011310", AREA_NATURE),
            Map.entry("011311", AREA_NATURE),
            Map.entry("011312", AREA_SOCIETY),
            Map.entry("011313", AREA_SOCIETY),
            Map.entry("011314", AREA_SOCIETY),
            Map.entry("011315", AREA_SOCIETY),
            Map.entry("009739", AREA_SOCIETY),
            Map.entry("011316", AREA_CULTURE),
            Map.entry("011317", AREA_CULTURE),
            Map.entry("011318", AREA_CULTURE),
            Map.entry("011319", AREA_CULTURE)
    );

    private static final Map<String, String> FUSION_AREA_BY_COURSE_CODE = Map.ofEntries(
            Map.entry("008881", AREA_FUSION),
            Map.entry("009937", AREA_FUSION),
            Map.entry("010538", AREA_FUSION),
            Map.entry("010797", AREA_FUSION),
            Map.entry("010798", AREA_FUSION),
            Map.entry("011877", AREA_FUSION),
            Map.entry("012056", AREA_FUSION),
            Map.entry("012080", AREA_FUSION),
            Map.entry("012082", AREA_FUSION),
            Map.entry("012083", AREA_FUSION)
    );

    public BalancedLiberalRequirement resolveRequirement(Integer admissionYear) {
        int year = admissionYear == null ? 0 : admissionYear;

        if (year >= 2024) {
            return new BalancedLiberalRequirement(9, 3, 2, 4);
        }
        if (year >= 2022) {
            return new BalancedLiberalRequirement(6, 2, 2, 4);
        }

        return new BalancedLiberalRequirement(0, 0, 1, 4);
    }

    public String resolveArea(Integer admissionYear, CompletedCourseUploadRowDto course) {
        String courseCode = normalizeCourseCode(course.courseCode());
        if (courseCode.isEmpty()) {
            return null;
        }

        String baseArea = BASE_AREA_BY_COURSE_CODE.get(courseCode);
        if (baseArea != null) {
            return baseArea;
        }

        if (!FUSION_AREA_BY_COURSE_CODE.containsKey(courseCode)) {
            return null;
        }

        int year = admissionYear == null ? 0 : admissionYear;
        Integer takenYear = parseTakenYear(course.year());
        if (year >= 2026) {
            return AREA_FUSION;
        }
        if (year >= 2022 && year <= 2025 && takenYear != null && takenYear >= 2026) {
            return AREA_FUSION;
        }

        return null;
    }

    public boolean isEligibleAcademicYear(Integer admissionYear, CompletedCourseUploadRowDto course) {
        BalancedLiberalRequirement requirement = resolveRequirement(admissionYear);
        Integer takenYear = parseTakenYear(course.year());
        if (takenYear == null || admissionYear == null) {
            return false;
        }

        int academicYear = takenYear - admissionYear + 1;
        return academicYear >= requirement.minAcademicYear() && academicYear <= requirement.maxAcademicYear();
    }

    private String normalizeCourseCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim();
    }

    private Integer parseTakenYear(String year) {
        if (year == null || year.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(year.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record BalancedLiberalRequirement(
            int requiredCredits,
            int requiredAreaCount,
            int minAcademicYear,
            int maxAcademicYear
    ) {
    }
}
