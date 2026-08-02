package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

import java.util.List;

@Getter
@Builder
public class OfferedCurriculumResponse {
    private final String departmentCode;
    private final String departmentName;
    private final int curriculumYear;
    private final int termYear;
    private final int semester;
    private final int curriculumCourseCount;
    private final int offeredCourseCount;
    private final int notOfferedCourseCount;
    private final List<OfferedCourseDto> offeredCourses;
    private final List<NotOfferedCourseDto> notOfferedCourses;

    @Getter
    @Builder
    public static class OfferedCourseDto {
        private final String abeekCourseCode;
        private final String courseName;
        private final CourseCategory category;
        private final CourseRole role;
        private final int credits;
        private final double designCredits;
        private final DesignLevel designLevel;
        private final String recommendedTerm;
        private final List<SectionDto> sections;
    }

    @Getter
    @Builder
    public static class SectionDto {
        private final String sejongCourseCode;
        private final String section;
        private final String category;
        private final String gradeYear;
        private final Double credits;
        private final String schedule;
        private final String room;
        private final String professor;
        private final String openingDepartment;
    }

    @Getter
    @Builder
    public static class NotOfferedCourseDto {
        private final String abeekCourseCode;
        private final String courseName;
        private final CourseCategory category;
        private final CourseRole role;
        private final String recommendedTerm;
    }
}
