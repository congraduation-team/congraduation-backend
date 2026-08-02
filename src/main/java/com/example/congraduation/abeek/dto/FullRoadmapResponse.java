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
    /** 선수/권장 과목 간선 (from → to). edgeType: MANDATORY(실선) | RECOMMENDED(점선) */
    private final List<RoadmapEdgeDto> edges;
    /**
     * 「모든 전공의 선수 과목」 영역에 넣을 과목 코드.
     * 검수 JSON commonMajorPrerequisiteCourseNames 를 커리큘럼 코드로 해석한 결과.
     */
    private final List<String> commonMajorPrerequisiteCourseCodes;
    /** 공통선수 영역 과목명 (해석 실패 포함 원본 유지용) */
    private final List<String> commonMajorPrerequisiteCourseNames;
    private final RoadmapSummaryDto summary;

    @Getter
    @Builder
    public static class RoadmapEdgeDto {
        private final String fromCourseCode;
        private final String fromCourseName;
        private final String toCourseCode;
        private final String toCourseName;
        /** MANDATORY | RECOMMENDED */
        private final String edgeType;
        private final boolean needsReview;
        private final String fromTerm;
        private final String toTerm;
    }

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
