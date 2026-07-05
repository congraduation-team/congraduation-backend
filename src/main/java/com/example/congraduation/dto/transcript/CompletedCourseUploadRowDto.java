package com.example.congraduation.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompletedCourseUploadRowDto(
        @Schema(description = "수강 연도", example = "2025")
        String year,
        @Schema(description = "수강 학기", example = "2학기")
        String semester,
        @Schema(description = "학수번호", example = "003278")
        String courseCode,
        @Schema(description = "교과목명", example = "컴퓨터구조")
        String courseName,
        @Schema(description = "이수구분", example = "전필")
        String category,
        @Schema(description = "해당 과목 학점", example = "3")
        String credit,
        @Schema(description = "평가방식", example = "GRADE")
        String evaluationMethod,
        @Schema(description = "등급", example = "A+")
        String grade,
        @Schema(description = "평점", example = "3.5")
        String gradePoint
) {
}
