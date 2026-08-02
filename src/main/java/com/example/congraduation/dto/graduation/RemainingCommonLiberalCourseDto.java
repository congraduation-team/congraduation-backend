package com.example.congraduation.dto.graduation;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record RemainingCommonLiberalCourseDto(
        @Schema(description = "남은 공통교양 대표 과목 정보")
        CategoryCourseDto course,
        @Schema(description = "이미 이수한 동등 과목명 목록")
        java.util.List<String> equivalentCompletedCourses
) {
}
