package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FullRoadmapResponse {
    private final String departmentCode;
    private final String departmentName;
    private final int curriculumYear;
    private final String studentId;
    private final String studentName;
    private final List<TermRoadmapDto> terms;
    private final List<RoadmapCourseDto> unscheduledCourses;
    private final RoadmapSummaryDto summary;

    @Getter
    @Builder
    public static class TermRoadmapDto {
        /** 예: 1-1 */
        private final String termKey;
        private final int gradeYear;
        private final int semester;
        /** term index 1..8 */
        private final int termIndex;
        private final Map<String, List<RoadmapCourseDto>> categories;
    }

    @Getter
    @Builder
    public static class RoadmapCourseDto {
        private final String abeekCourseCode;
        private final String courseName;
        private final CourseCategory category;
        /** 전문교양 / BSM / 전공 */
        private final String categoryLabel;
        private final boolean professionalLiberal;
        private final boolean bsm;
        private final boolean abeekMajor;
        private final CourseRole role;
        private final String roleLabel;
        private final int credits;
        private final double designCredits;
        private final boolean hasDesignCredits;
        private final DesignLevel designLevel;
        private final String recommendedTerm;
        private final boolean newlyIntroducedRequired;
        private final boolean completed;
        private final Integer takenYear;
        private final Integer takenSemester;
        private final List<String> prerequisiteCourseCodes;
    }

    @Getter
    @Builder
    public static class RoadmapSummaryDto {
        private final int totalCourses;
        private final int completedCourses;
        private final int professionalLiberalCount;
        private final int bsmCount;
        private final int majorCount;
        private final double totalDesignCredits;
        private final double completedDesignCredits;
    }
}
