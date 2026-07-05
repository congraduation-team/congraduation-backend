package com.example.congraduation.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryCourseDto(
        @Schema(description = "학수번호", example = "003278")
        String courseCode,
        @Schema(description = "교과목명", example = "컴퓨터구조")
        String courseName,
        @Schema(description = "해당 과목 학점", example = "3")
        String credit
) {
}
