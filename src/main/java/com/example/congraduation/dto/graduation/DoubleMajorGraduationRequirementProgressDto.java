package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record DoubleMajorGraduationRequirementProgressDto(
        @Schema(description = "복수전공 추가 요건 적용 여부", example = "true")
        boolean required,
        @Schema(description = "복수전공 추가 요건 자동 충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "진행 상태", example = "IN_PROGRESS")
        String status,
        @Schema(description = "상세 설명", example = "예체능대학 복수전공은 졸업작품(시험) 추가 이수가 필요합니다.")
        String detail
) {
}
