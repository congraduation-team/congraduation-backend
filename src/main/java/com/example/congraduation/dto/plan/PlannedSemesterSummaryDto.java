package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlannedSemesterSummaryDto(
        @Schema(description = "계획 수강 연도", example = "2026")
        Integer targetYear,
        @Schema(description = "계획 수강 학기", example = "2")
        Integer targetSemester,
        @Schema(description = "해당 학기 계획 학점 합계", example = "15")
        String totalCredits,
        @Schema(description = "해당 학기 계획 과목 목록")
        List<PlannedCourseResponseDto> courses
) {
}
