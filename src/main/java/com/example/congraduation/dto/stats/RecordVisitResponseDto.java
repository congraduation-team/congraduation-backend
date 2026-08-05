package com.example.congraduation.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "방문 기록 결과")
public record RecordVisitResponseDto(
        @Schema(description = "기록에 사용된 visitorKey", example = "student:12")
        String visitorKey,

        @Schema(description = "당일 첫 방문으로 신규 집계됐는지", example = "true")
        boolean newlyCountedToday
) {
}
