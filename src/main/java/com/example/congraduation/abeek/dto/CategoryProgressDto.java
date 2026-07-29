package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CategoryProgressDto {
    private final String category;
    private final double earnedCredits;
    private final double requiredCredits;
    private final double progressPercent;
    private final boolean satisfied;
    private final String requirementSource;
    /** 해당 카테고리로 분류된 이수 과목 전체 (일부만 잘라내지 않음) */
    private final int completedCourseCount;
    private final List<CompletedCourseDto> completedCourses;

    @Getter
    @Builder
    public static class CompletedCourseDto {
        private final String courseCode;
        private final String courseName;
        private final int credits;
        private final double designCredits;
        private final Integer takenYear;
        private final Integer takenSemester;
    }
}
