package com.example.hackathon.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CategorySummaryDto(
        @Schema(description = "이수구분", example = "전필")
        String category,
        @Schema(description = "해당 이수구분 총 이수학점", example = "27")
        String earnedCredits,
        @Schema(description = "해당 이수구분 필요 이수학점", example = "33")
        String requiredCredits,
        @Schema(description = "해당 이수구분 충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "해당 이수구분 진행률(%)", example = "81.82")
        String progressPercent,
        @Schema(description = "해당 이수구분에 속한 과목 목록")
        List<CategoryCourseDto> courses
) {
}
