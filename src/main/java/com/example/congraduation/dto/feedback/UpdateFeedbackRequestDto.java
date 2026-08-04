package com.example.congraduation.dto.feedback;

import com.example.congraduation.domain.feedback.FeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 문의/오류 처리 업데이트")
public record UpdateFeedbackRequestDto(
        @Schema(description = "처리 상태", example = "IN_PROGRESS")
        FeedbackStatus status,
        @Schema(description = "관리자 메모", example = "성적 매핑 로직 확인 중")
        String adminNote
) {
}
