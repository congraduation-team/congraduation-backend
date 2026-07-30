package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlannableCourseCatalogResponseDto(
        @Schema(description = "중복 제거된 교과목 수", example = "820")
        int count,
        @Schema(description = "계획용 교과목 목록")
        List<PlannableCourseDto> courses
) {
}
