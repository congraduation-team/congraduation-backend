package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record SwCodingCertificationProgressDto(
        @Schema(description = "SW코딩졸업인증 정책 적용 대상 여부", example = "true")
        boolean applicable,
        @Schema(description = "SW코딩졸업인증 충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "진행 상태", example = "IN_PROGRESS")
        String status,
        @Schema(description = "학생 구분", example = "MAJOR")
        String studentGroup,
        @Schema(description = "적용되는 졸업인증 규칙 설명", example = "영어/고전독서/SW코딩인증 중 2개 이상 통과")
        String graduationRule,
        @Schema(description = "주요 충족 기준", example = "TOSC 3급 이상")
        String primaryRequirement,
        @Schema(description = "대체이수 기준", example = "고급C프로그래밍및실습 B0 이상")
        String substituteRequirement,
        @Schema(description = "현재 판정 상세", example = "고급C프로그래밍및실습 과목을 B0 이상으로 이수해 SW코딩졸업인증 대체요건을 충족했습니다.")
        String detail
) {
}
