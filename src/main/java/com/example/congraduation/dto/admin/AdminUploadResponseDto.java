package com.example.congraduation.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminUploadResponseDto(
        @Schema(description = "결과 메시지")
        String message,
        @Schema(description = "적재된 개설 강좌 수", example = "2595")
        int count,
        @Schema(description = "연도", example = "2026")
        int year,
        @Schema(description = "학기 (1/2/3/4)", example = "1")
        int semester,
        @Schema(description = "GitHub timetable-data 커밋 반영 여부")
        boolean githubSynced,
        @Schema(description = "GitHub 커밋 URL (성공 시)")
        String githubCommitUrl
) {
}
