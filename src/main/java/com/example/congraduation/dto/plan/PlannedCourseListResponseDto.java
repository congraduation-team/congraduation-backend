package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlannedCourseListResponseDto(
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(description = "현재까지 완료한 마지막 정규 학기", example = "2-1")
        String lastCompletedSemester,
        @Schema(description = "전체 계획 학점 합계", example = "42")
        String totalPlannedCredits,
        @Schema(description = "학기별 계획 목록")
        List<PlannedSemesterSummaryDto> semesters
) {
}
