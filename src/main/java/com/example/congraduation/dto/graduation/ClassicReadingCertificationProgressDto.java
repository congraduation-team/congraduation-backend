package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record ClassicReadingCertificationProgressDto(
        @Schema(description = "고전독서인증 정책 적용 대상 여부", example = "true")
        boolean applicable,
        @Schema(description = "고전독서인증 충족 또는 면제 여부", example = "true")
        boolean satisfied,
        @Schema(description = "진행 상태", example = "CERTIFIED")
        String status,
        @Schema(description = "현재 정책 분류", example = "REQUIRED")
        String policyType,
        @Schema(description = "기본 기준 설명", example = "고전독서인증 통과 또는 고전특강 이수")
        String primaryRequirement,
        @Schema(description = "대체이수 기준", example = "고전특강 이수")
        String substituteRequirement,
        @Schema(description = "현재 판정 상세", example = "고전특강 이수로 고전독서인증 대체요건을 충족했습니다.")
        String detail
) {
}
