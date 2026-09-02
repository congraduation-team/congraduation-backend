package com.example.congraduation.dto.feedback;

import com.example.congraduation.domain.feedback.Feedback;
import com.example.congraduation.domain.feedback.FeedbackStatus;
import com.example.congraduation.domain.feedback.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Schema(description = "오류 신고 / 문의 항목")
public record FeedbackResponseDto(
        Long id,
        FeedbackType type,
        String title,
        String content,
        FeedbackStatus status,
        Long studentId,
        String studentNo,
        String studentName,
        String major,
        String createdAt,
        String updatedAt,
        String adminNote
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_KST = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static FeedbackResponseDto from(Feedback feedback) {
        Long studentId = feedback.getStudent() == null ? null : feedback.getStudent().getId();
        return new FeedbackResponseDto(
                feedback.getId(),
                feedback.getType(),
                feedback.getTitle(),
                feedback.getContent(),
                feedback.getStatus(),
                studentId,
                feedback.getStudentNo(),
                feedback.getStudentName(),
                feedback.getMajor(),
                formatKst(feedback.getCreatedAt()),
                formatKst(feedback.getUpdatedAt()),
                feedback.getAdminNote()
        );
    }

    private static String formatKst(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(KST).format(ISO_KST);
    }
}
