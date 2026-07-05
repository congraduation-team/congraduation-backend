package com.example.congraduation.dto.transcript;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TranscriptUploadResponseDto(
        @Schema(description = "파싱된 과목 수", example = "64")
        int count,
        @Schema(description = "성적 요약 정보")
        TranscriptSummaryDto summary,
        @Schema(description = "파싱된 과목 목록")
        List<CompletedCourseUploadRowDto> courses
) {

    public static TranscriptUploadResponseDto from(
            List<CompletedCourseUploadRowDto> courses,
            TranscriptSummaryDto summary
    ) {
        return new TranscriptUploadResponseDto(courses.size(), summary, courses);
    }
}
