package com.example.hackathon.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TranscriptSummaryDto(
        @Schema(description = "총 이수학점", example = "130")
        String totalCredits,
        @Schema(description = "총 평점합(학점 x 평점의 합)", example = "421.5")
        String totalGradePoints,
        @Schema(description = "평균 평점", example = "3.24")
        String averageGradePoint,
        @Schema(description = "이수구분별 요약")
        List<CategorySummaryDto> categorySummaries
) {
}
