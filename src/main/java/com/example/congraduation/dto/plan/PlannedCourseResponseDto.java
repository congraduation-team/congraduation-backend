package com.example.congraduation.dto.plan;

import com.example.congraduation.domain.plan.PlannedCourse;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlannedCourseResponseDto(
        @Schema(description = "계획 과목 ID", example = "1")
        Long id,
        @Schema(description = "계획 수강 연도", example = "2026")
        Integer targetYear,
        @Schema(description = "계획 수강 학기", example = "2")
        Integer targetSemester,
        @Schema(description = "학수번호", example = "003278")
        String courseCode,
        @Schema(description = "교과목명", example = "컴퓨터구조")
        String courseName,
        @Schema(description = "이수구분", example = "전필")
        String category,
        @Schema(description = "학점", example = "3")
        String credit
) {

    public static PlannedCourseResponseDto from(PlannedCourse plannedCourse) {
        return new PlannedCourseResponseDto(
                plannedCourse.getId(),
                plannedCourse.getTargetYear(),
                plannedCourse.getTargetSemester(),
                plannedCourse.getCourseCode(),
                plannedCourse.getCourseName(),
                plannedCourse.getCategory(),
                plannedCourse.getCredit()
        );
    }
}
