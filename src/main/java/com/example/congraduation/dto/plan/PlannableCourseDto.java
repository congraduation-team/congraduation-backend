package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlannableCourseDto(
        @Schema(description = "학수번호 목록", example = "[\"007123\"]")
        List<String> courseCodes,
        @Schema(description = "교과목명", example = "컴퓨터구조")
        String courseName,
        @Schema(description = "이수구분 목록", example = "[\"전공필수\", \"전공선택\"]")
        List<String> categories,
        @Schema(description = "개설학과 목록", example = "[\"컴퓨터공학과\"]")
        List<String> departments,
        @Schema(description = "대상 학년 목록", example = "[\"2\", \"3\"]")
        List<String> targetGrades,
        @Schema(description = "학점 목록", example = "[\"3.0\"]")
        List<String> credits,
        @Schema(description = "최근 1년 기준 개설 학기 목록", example = "[\"2025-2\", \"2026-1\"]")
        List<String> offeredTerms
) {
}
