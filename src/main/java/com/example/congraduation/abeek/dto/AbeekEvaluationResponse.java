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

    private final List<String> messages;
}
