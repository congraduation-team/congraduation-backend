package com.example.congraduation.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;

public record TranscriptStatusResponseDto(
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(description = "성적표 업로드 여부", example = "true")
        boolean hasTranscript
) {
}
