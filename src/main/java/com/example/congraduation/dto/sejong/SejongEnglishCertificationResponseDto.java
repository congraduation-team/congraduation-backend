package com.example.congraduation.dto.sejong;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세종 영어졸업인증 현황")
public record SejongEnglishCertificationResponseDto(
        @Schema(description = "영어인증 제출 여부", example = "true")
        boolean submitted,
        @Schema(description = "세종 사이트 기준 인증 완료 여부", example = "true")
        boolean certified,
        @Schema(description = "세종 사이트 상태값", example = "인증완료")
        String status,
        @Schema(description = "제출 시험 종류", example = "TOEIC")
        String examType,
        @Schema(description = "제출 점수", example = "850")
        String score,
        @Schema(description = "제출일", example = "2026-08-04")
        String submitDate,
        @Schema(description = "현재 상태 설명", example = "세종 영어인증 사이트에서 인증 완료로 확인되었습니다.")
        String detail
) {

    public static SejongEnglishCertificationResponseDto notSubmitted() {
        return new SejongEnglishCertificationResponseDto(
                false,
                false,
                "NOT_SUBMITTED",
                null,
                null,
                null,
                "세종 영어인증 사이트 기준 제출 내역이 없습니다."
        );
    }

    public static SejongEnglishCertificationResponseDto unavailable() {
        return new SejongEnglishCertificationResponseDto(
                false,
                false,
                "UNAVAILABLE",
                null,
                null,
                null,
                "세종 영어인증 사이트 조회에 실패했습니다."
        );
    }
}
