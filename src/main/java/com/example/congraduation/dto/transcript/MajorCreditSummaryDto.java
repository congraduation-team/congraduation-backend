package com.example.congraduation.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이수 과목 기준 전필/전선 학점 합계")
public record MajorCreditSummaryDto(
        @Schema(description = "전필(전공필수) 이수 학점", example = "36")
        double requiredMajorCredits,
        @Schema(description = "전선(전공선택) 이수 학점", example = "24")
        double electiveMajorCredits,
        @Schema(description = "전필+전선 합계", example = "60")
        double totalMajorCredits,
        @Schema(description = "전필로 집계된 과목 수", example = "12")
        int requiredMajorCourseCount,
        @Schema(description = "전선으로 집계된 과목 수", example = "8")
        int electiveMajorCourseCount,
        @Schema(description = "집계에 사용된 전체 이수 과목 수", example = "40")
        int totalCourseCount
) {
}
