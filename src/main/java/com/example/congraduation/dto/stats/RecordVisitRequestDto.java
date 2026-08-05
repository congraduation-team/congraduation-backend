package com.example.congraduation.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "방문 기록 요청. FE는 브라우저당 고정 visitorKey(localStorage UUID)를 보내는 것을 권장합니다.")
public record RecordVisitRequestDto(
        @Schema(description = "익명 방문자 키 (예: localStorage UUID). studentId가 없으면 필수.", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String visitorKey,

        @Schema(description = "로그인한 학생 DB PK (있으면 student:{id}로 집계)", example = "12")
        Long studentId
) {
}
