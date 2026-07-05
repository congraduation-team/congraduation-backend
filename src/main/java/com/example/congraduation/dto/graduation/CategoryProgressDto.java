package com.example.congraduation.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryProgressDto(
        @Schema(description = "현재 이수 학점", example = "13")
        String earnedCredits,
        @Schema(description = "필요 이수 학점", example = "21")
        String requiredCredits,
        @Schema(description = "충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "진행률(%)", example = "61.90")
        String progressPercent
) {
}
