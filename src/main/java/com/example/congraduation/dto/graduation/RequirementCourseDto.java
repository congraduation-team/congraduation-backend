package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record RequirementCourseDto(
        @Schema(description = "커리큘럼 과목 코드", example = "CSE_operating_system_abc123")
        String courseCode,
        @Schema(description = "교과목명", example = "운영체제")
        String courseName,
        @Schema(description = "학점", example = "3")
        String credit,
        @Schema(description = "권장 학년학기", example = "3-1")
        String recommendedTerm
) {
}
