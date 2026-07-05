package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record MajorCreditSummaryDto(
        @Schema(description = "전공학점 총합(전필+전선+전기)", example = "45")
        String earnedMajorCredits,
        @Schema(description = "필요 전공학점", example = "72")
        String requiredMajorCredits,
        @Schema(description = "전공학점 총합 충족 여부", example = "false")
        boolean majorCreditsSatisfied,
        @Schema(description = "전공학점 총합 진행률(%)", example = "62.50")
        String majorCreditsProgressPercent,
        @Schema(description = "전공필수 이수 학점", example = "18")
        String earnedMajorRequiredCredits,
        @Schema(description = "필요 전공필수 학점", example = "33")
        String requiredMajorRequiredCredits,
        @Schema(description = "전공필수 충족 여부", example = "false")
        boolean majorRequiredSatisfied,
        @Schema(description = "전공필수 진행률(%)", example = "54.55")
        String majorRequiredProgressPercent,
        @Schema(description = "전공선택 이수 학점", example = "21")
        String earnedMajorElectiveCredits,
        @Schema(description = "필요 전공선택 학점", example = "39")
        String requiredMajorElectiveCredits,
        @Schema(description = "전공선택 충족 여부", example = "false")
        boolean majorElectiveSatisfied,
        @Schema(description = "전공선택 진행률(%)", example = "53.85")
        String majorElectiveProgressPercent,
        @Schema(description = "전공기초 이수 학점", example = "6")
        String earnedMajorFoundationCredits
) {
}
