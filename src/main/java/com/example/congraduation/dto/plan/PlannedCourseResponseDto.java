package com.example.congraduation.dto.plan;

import com.example.congraduation.domain.plan.PlannedCourse;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlannedCourseResponseDto(
        @Schema(description = "계획 과목 ID", example = "1")
        Long id,
        @Schema(description = "계획 학기 ID", example = "1")
        Long plannedSemesterId,
        @Schema(description = "학년", example = "2")
        Integer gradeYear,
        @Schema(description = "학기", example = "2")
        Integer semester,
        @Schema(description = "학수번호", example = "003278")
        String courseCode,
        @Schema(description = "교과목명", example = "컴퓨터구조")
        String courseName,
        @Schema(description = "이수구분", example = "전필")
        String category,
        @Schema(description = "학점", example = "3")
        String credit,
        @Schema(description = "예상 성적", example = "A+")
        String expectedGrade,
        @Schema(description = "예상 평점", example = "4.5")
        String expectedGradePoint
) {

    public static PlannedCourseResponseDto from(PlannedCourse plannedCourse) {
        return new PlannedCourseResponseDto(
                plannedCourse.getId(),
                plannedCourse.getPlannedSemesterId(),
                plannedCourse.getGradeYear(),
                plannedCourse.getSemester(),
                plannedCourse.getCourseCode(),
                plannedCourse.getCourseName(),
                plannedCourse.getCategory(),
                plannedCourse.getCredit(),
                plannedCourse.getExpectedGrade(),
                PlannedCourseGradePolicy.toGradePoint(plannedCourse.getExpectedGrade())
        );
    }
}
