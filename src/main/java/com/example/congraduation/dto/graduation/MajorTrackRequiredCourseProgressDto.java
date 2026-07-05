package com.example.congraduation.dto.graduation;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MajorTrackRequiredCourseProgressDto(
        @Schema(description = "학과지정 전필 정책 적용 여부", example = "true")
        boolean policyApplied,
        @Schema(description = "필요 지정 과목 수", example = "4")
        Integer requiredCourseCount,
        @Schema(description = "이수 완료한 지정 과목 수", example = "3")
        Integer completedCourseCount,
        @Schema(description = "학과지정 전필 충족 여부", example = "false")
        boolean satisfied,
        @Schema(description = "이수한 지정 과목")
        List<CategoryCourseDto> completedCourses,
        @Schema(description = "미이수 지정 과목")
        List<CategoryCourseDto> missingCourses
) {
}
