package com.example.congraduation.dto.graduation;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MissingBalancedLiberalAreaDto(
        @Schema(description = "미충족 균형교양 영역", example = "문화와예술")
        String area,
        @Schema(description = "해당 영역에서 선택 가능한 대표 과목 목록")
        List<CategoryCourseDto> candidateCourses
) {
}
