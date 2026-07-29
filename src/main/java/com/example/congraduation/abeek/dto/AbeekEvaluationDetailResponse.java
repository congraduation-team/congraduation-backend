package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

import java.util.List;

@Getter
@Builder
public class AbeekEvaluationDetailResponse {
    private final String studentId;
    private final String studentNo;
    private final String studentName;
    private final int entranceYear;
    private final int graduationAbeekYear;
    /** 표시용. 예: "2026 졸업예정 기준" */
    private final String graduationAbeekBasisLabel;
    private final AbeekEvaluationResponse evaluation;
    /** 카테고리 분류 안 된 것 포함, 이 학생의 ABEEK 매칭된 전체 이수 과목 */
    private final List<CourseDetailDto> allCompletedCourses;
    private final int allCompletedCourseCount;
    private final List<CategoryDetailDto> categories;

    @Getter
    @Builder
    public static class CategoryDetailDto {
        private final String categoryKey;
        private final String categoryLabel;
        private final CategoryProgressDto progress;
        private final int completedCourseCount;
        private final List<CourseDetailDto> completedCourses;
        private final List<CourseDetailDto> remainingCourses;
    }

    @Getter
    @Builder
    public static class CourseDetailDto {
        private final String courseCode;
        private final String courseName;
        private final CourseCategory category;
        private final String categoryLabel;
        private final CourseRole role;
        private final String roleLabel;
        private final int credits;
        private final double designCredits;
        private final DesignLevel designLevel;
        private final boolean completed;
        private final boolean waived;
        private final Integer takenYear;
        private final Integer takenSemester;
        private final String note;
    }
}
