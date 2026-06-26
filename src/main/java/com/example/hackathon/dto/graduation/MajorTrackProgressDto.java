package com.example.hackathon.dto.graduation;

import com.example.hackathon.domain.MajorType;
import io.swagger.v3.oas.annotations.media.Schema;

public record MajorTrackProgressDto(
        @Schema(description = "전공 트랙 유형", example = "DOUBLE_MAJOR")
        MajorType trackType,
        @Schema(description = "학과명", example = "경제학과")
        String department,
        @Schema(description = "총 전공학점 진행도")
        CreditProgressDto totalCredits,
        @Schema(description = "전공필수 진행도")
        CreditProgressDto requiredCredits,
        @Schema(description = "전공선택 진행도")
        CreditProgressDto electiveCredits,
        @Schema(description = "복수전공 이수구분 집계 기준", example = "복필/복선")
        String categoryBasis,
        @Schema(description = "진행 상태", example = "IN_PROGRESS")
        String status
) {
}
