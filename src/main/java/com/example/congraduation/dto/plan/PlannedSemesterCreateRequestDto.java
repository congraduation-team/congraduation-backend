package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlannedSemesterCreateRequestDto(
        @Schema(description = "한 번에 추가할 빈 학기 수", example = "1")
        Integer count
) {
}
