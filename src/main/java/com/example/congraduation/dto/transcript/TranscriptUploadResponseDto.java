package com.example.congraduation.dto.transcript;

import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TranscriptUploadResponseDto(
        @Schema(description = "앱 Student DB PK", example = "1")
        Long studentDbId,
        @Schema(description = "학번(studentNo). ABEEK API path의 studentId로 사용", example = "21012345")
        String studentNo,
        @Schema(description = "파싱된 과목 수", example = "64")
        int count,
        @Schema(description = "성적 요약 정보")
        TranscriptSummaryDto summary,
        @Schema(description = "파싱된 과목 목록")
        List<CompletedCourseUploadRowDto> courses,
        @Schema(description = "ABEEK 동기화/판정 결과 (성공 시)")
        AbeekTranscriptEvaluationResponse abeek,
        @Schema(description = "ABEEK 동기화 실패 시 원인 메시지. 성적 저장은 성공한 상태일 수 있음")
        String abeekError
) {

    public static TranscriptUploadResponseDto from(
            Long studentDbId,
            String studentNo,
            List<CompletedCourseUploadRowDto> courses,
            TranscriptSummaryDto summary,
            AbeekTranscriptEvaluationResponse abeek,
            String abeekError
    ) {
        return new TranscriptUploadResponseDto(
                studentDbId,
                studentNo,
                courses.size(),
                summary,
                courses,
                abeek,
                abeekError
        );
    }
}
