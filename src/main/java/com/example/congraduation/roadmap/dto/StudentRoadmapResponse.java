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
         * GENERAL=교양필수, FOUNDATION=기초필수, MAJOR=전공.
         * 공학인증 대상이면 BSM(학문기초)도 포함.
         */
        private final Map<String, List<RoadmapCourseDto>> categories;
    }

    @Getter
    @Builder
    public static class RoadmapCourseDto {
        @Schema(description = "학수번호", example = "009960")
        private final String courseCode;
        @Schema(description = "과목명. 기이수면 성적표(이수 당시) 이름")
        private final String courseName;
        @Schema(description = "동일과목 학수번호(자기 자신 포함)")
        private final List<String> equivalentCourseCodes;
        @Schema(description = "동일과목 과목명(개편 전후 표기)")
        private final List<String> equivalentCourseNames;
        @Schema(description = "표시 이수구분. 교양필수|기초필수|전공필수|전공선택 등", example = "기초필수")
        private final String category;
        @Schema(description = "GENERAL|BSM|MAJOR|OTHER. 기초필수→BSM, 교양필수→GENERAL")
        private final String abeekBucket;
        private final Double credits;
        private final boolean completed;
        @Schema(description = "실제 수강 연도(원본). 학년 계산에 쓰지 말 것. standingTermKey / terms.termKey 사용")
        private final String takenYear;
        @Schema(description = "실제 수강 학기(원본). 예: 1학기")
        private final String takenSemester;
        @Schema(
                description = "기이수 정규학기 순번 기준 배치 칸(1-1~4-2). "
                        + "takenYear-admissionYear+1 달력 계산 금지. 휴학 건너뛴 순번.",
                example = "2-1"
        )
        private final String standingTermKey;
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
