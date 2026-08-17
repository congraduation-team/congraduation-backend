package com.example.congraduation.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사이트 방문·실사용 통계")
public record SiteStatsResponseDto(
        @Schema(description = "당일 로그인 순 방문자 수", example = "42")
        long todayVisitors,

        @Schema(description = "이번 달 로그인 순 방문자 수", example = "380")
        long monthlyVisitors,

        @Schema(description = "누적 로그인 순 방문자 수", example = "1520")
        long totalVisitors,

        @Schema(description = "기이수(성적표)를 업로드한 실사용자 수", example = "890")
        long transcriptUsers,

        @Schema(description = "통계 기준 타임존", example = "Asia/Seoul")
        String timezone,

        @Schema(description = "당일 기준일 (yyyy-MM-dd)", example = "2026-08-05")
        String today,

        @Schema(description = "월 집계 시작일 (yyyy-MM-dd)", example = "2026-08-01")
        String monthStart
) {
}
