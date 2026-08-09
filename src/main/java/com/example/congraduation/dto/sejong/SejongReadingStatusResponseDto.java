package com.example.congraduation.dto.sejong;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "세종 고전독서 인증 현황")
public record SejongReadingStatusResponseDto(
        @Schema(description = "전체 인증 완료 여부", example = "true")
        boolean completed,
        @Schema(description = "상단 제목", example = "현재 고전독서인증 완료!")
        String title,
        @Schema(description = "상단 부제목", example = "고전독서인증 현황")
        String subtitle,
        @Schema(description = "하단 안내 문구", example = "고전독서인증이 인증되었습니다.")
        String message,
        @Schema(description = "영역별 인증 현황")
        List<AreaStatusDto> areas
) {

    @Schema(description = "영역별 고전독서 인증 현황")
    public record AreaStatusDto(
            @Schema(description = "영역명", example = "서양의 역사와 사상")
            String name,
            @Schema(description = "해당 영역 이수 권수", example = "5")
            int completedCount,
            @Schema(description = "해당 영역 인증 권수", example = "5")
            int certifiedCount,
            @Schema(description = "해당 영역 필요 권수", example = "4")
            int requiredCount,
            @Schema(description = "해당 영역 충족 여부", example = "true")
            boolean satisfied
    ) {
    }
}
