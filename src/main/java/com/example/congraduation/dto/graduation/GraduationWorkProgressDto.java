package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record GraduationWorkProgressDto(
        @Schema(description = "졸업작품 필요 여부", example = "true")
        boolean required,
        @Schema(description = "졸업작품 충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "졸업작품 상태", example = "IN_PROGRESS")
        String status,
        @Schema(description = "졸업작품 기준 구분", example = "MAJOR_REQUIRED")
        String requirementType,
        @Schema(description = "인정된 과목명 또는 안내", example = "졸업작품")
        String detail
) {
}
