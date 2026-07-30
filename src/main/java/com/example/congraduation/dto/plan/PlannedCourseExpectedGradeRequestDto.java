package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlannedCourseExpectedGradeRequestDto(
        @Schema(description = "예상 성적", example = "A+", allowableValues = {"A+", "A0", "B+", "B0", "C+", "C0", "D+", "D0", "F", "P", "NP"})
        String expectedGrade
) {
}
