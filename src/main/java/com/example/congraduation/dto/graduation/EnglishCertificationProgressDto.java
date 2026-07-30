package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record EnglishCertificationProgressDto(
        @Schema(description = "영어졸업인증 정책 적용 대상 여부", example = "true")
        boolean applicable,
        @Schema(description = "영어졸업인증 충족 또는 면제 여부", example = "true")
        boolean satisfied,
        @Schema(description = "진행 상태", example = "EXEMPTED")
        String status,
        @Schema(description = "현재 정책 분류", example = "REQUIRED")
        String policyType,
        @Schema(description = "기본 기준 설명", example = "공인영어 기준 점수 또는 Intensive English 이수")
        String primaryRequirement,
        @Schema(description = "현재 판정 상세", example = "Intensive English 이수로 영어졸업인증이 면제됩니다.")
        String detail
) {
}
