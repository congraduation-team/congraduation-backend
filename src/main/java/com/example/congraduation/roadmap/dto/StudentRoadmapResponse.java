package com.example.congraduation.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class StudentRoadmapResponse {

    @Schema(description = "앱 Student DB PK. 조회만 한 경우 null")
    private final Long studentDbId;

    @Schema(description = "학번")
    private final String studentNo;

    private final String studentName;

    @Schema(description = "로드맵 기준 학과명 (시간표 개설학과)")
    private final String departmentName;

    @Schema(description = "공학인증 대상 학과 여부")
    private final boolean abeekTarget;

    @Schema(description = "공학인증 학과코드 (대상일 때만)", example = "CSE")
    private final String abeekDepartmentCode;

    @Schema(description = "로드맵에 사용한 시간표 학기들")
    private final List<SourceTermDto> sourceTerms;

    private final List<TermRoadmapDto> terms;

    private final RoadmapSummaryDto summary;

    @Getter
    @Builder
    public static class SourceTermDto {
        private final int termYear;
        private final int semester;
        private final int offeringCount;
    }

    @Getter
    @Builder
    public static class TermRoadmapDto {
        /** 예: 1-1 */
        private final String termKey;
        private final int gradeYear;
        private final int semester;
        private final int termIndex;
        /** 모든 학생 공통: 해당 학기 과목 (중복 분반 제거) */
        private final List<RoadmapCourseDto> courses;
        /**
         * 공학인증 대상만 채움. GENERAL / BSM / MAJOR.
         * 비대상이면 null.
         */
        private final Map<String, List<RoadmapCourseDto>> categories;
    }

    @Getter
    @Builder
    public static class RoadmapCourseDto {
        @Schema(description = "학수번호", example = "009960")
        private final String courseCode;
        private final String courseName;
        @Schema(description = "시간표 이수구분 원문. 학문기초성 전공기초는 기초필수로 정규화", example = "기초필수")
        private final String category;
        @Schema(description = "공학인증 분할용. GENERAL|BSM|MAJOR|OTHER. 기초필수는 BSM")
        private final String abeekBucket;
        private final Double credits;
        private final boolean completed;
        private final String takenYear;
        private final String takenSemester;
        private final String grade;
        private final int sectionCount;
    }

    @Getter
    @Builder
    public static class RoadmapSummaryDto {
        private final int totalCourses;
        private final int completedCourses;
        private final int generalCount;
        private final int bsmCount;
        private final int majorCount;
    }
}
