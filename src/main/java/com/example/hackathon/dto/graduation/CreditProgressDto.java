package com.example.hackathon.dto.graduation;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreditProgressDto(
        @Schema(description = "현재 이수 학점", example = "98")
        String earnedCredits,
        @Schema(description = "필요 이수 학점", example = "130")
        String requiredCredits,
        @Schema(description = "충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "진행률(%)", example = "75.38")
        String progressPercent
) {
}
