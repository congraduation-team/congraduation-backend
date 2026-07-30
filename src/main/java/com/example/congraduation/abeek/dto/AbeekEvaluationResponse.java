package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AbeekEvaluationResponse {
    /** 학번(studentNo). DB PK 아님. */
    private final String studentId;
    /** 학번. studentId와 동일. */
    private final String studentNo;
    private final String studentName;
    private final int entranceYear;
    private final int graduationAbeekYear;
    /** 졸업 예정 연도 (표시용). 예: 2026-1·4-1 → 2027 */
    private final int expectedGraduationYear;
    /** 표시용. 예: "2027년 졸업 예정 기준" */
    private final String graduationAbeekBasisLabel;
    private final boolean overallSatisfied;

    private final CategoryProgressDto general;
    private final CategoryProgressDto bsm;
    private final CategoryProgressDto major;
    private final CategoryProgressDto design;

    /**
     * 2020~2021 입학처럼 입학 연도에 인증선택 요건이 없으면 false.
     * 프론트는 이 값이 false일 때 인증선택 섹션을 숨기거나 N/A로 표시하면 된다.
     */
    private final boolean certElectiveApplicable;
    /** 인증선택 과목 수 기준 진행 (미적용이면 required=0, satisfied=true). */
    private final CategoryProgressDto certElective;

    private final boolean designSequenceSatisfied;
    private final DesignEvaluationResult designDetail;

    private final List<RequiredCourseStatusDto> entranceRequiredCourses;
    private final List<RequiredCourseStatusDto> waivedGraduationOnlyCourses;

    private final RequirementSummaryDto requirementSummary;
    private final List<String> messages;

    /** ABEEK에 매칭된 전체 이수 과목 (카테고리 무관, 일부 생략 없음) */
    private final int allCompletedCourseCount;
    private final List<CategoryProgressDto.CompletedCourseDto> allCompletedCourses;

    @Getter
    @Builder
    public static class RequirementSummaryDto {
        private final int entranceYear;
        private final int graduationAbeekYear;
        private final int expectedGraduationYear;
        /** 표시용. 예: "2027년 졸업 예정 기준" */
        private final String graduationAbeekBasisLabel;
        private final String appliedBasis;
        private final int generalMinCredits;
        private final int bsmMinCredits;
        private final int majorMinCredits;
        private final double designMinCredits;
        private final boolean certElectiveApplicable;
        private final int certElectiveMinCourses;
        private final int certElectiveMinCredits;
        private final boolean designSequenceSatisfied;
        private final boolean hasBasicDesign;
        private final boolean hasElementDesign;
        private final boolean hasComprehensiveDesign;
        private final List<String> waivedCourses;
        private final List<String> notes;
    }
}
