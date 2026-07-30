package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlannedSemesterSummaryDto(
        @Schema(description = "계획 학기 ID", example = "1")
        Long plannedSemesterId,
        @Schema(description = "학년", example = "2")
        Integer gradeYear,
        @Schema(description = "학기", example = "2")
        Integer semester,
        @Schema(description = "해당 학기 계획 학점 합계", example = "15")
        String totalCredits,
        @Schema(description = "빈 학기 여부", example = "true")
        boolean empty,
        @Schema(description = "해당 학기 계획 과목 목록")
        List<PlannedCourseResponseDto> courses
) {
}
