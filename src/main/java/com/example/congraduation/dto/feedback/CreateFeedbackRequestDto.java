package com.example.congraduation.dto.feedback;

import com.example.congraduation.domain.feedback.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오류 신고 / 문의 접수 요청")
public record CreateFeedbackRequestDto(
        @Schema(description = "유형", example = "BUG", requiredMode = Schema.RequiredMode.REQUIRED)
        FeedbackType type,
        @Schema(description = "제목 (최대 80자)", example = "기이수 성적 반영 오류", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(description = "내용 (최대 2000자)", example = "업로드한 성적표의 A+ 과목이 반영되지 않습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        String content,
        @Schema(description = "학생 DB PK", example = "1")
        Long studentId,
        @Schema(description = "학번 스냅샷", example = "21012345")
        String studentNo,
        @Schema(description = "이름 스냅샷", example = "홍길동")
        String studentName,
        @Schema(description = "전공 스냅샷", example = "컴퓨터공학과")
        String major
) {
}
