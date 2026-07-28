package com.example.congraduation.abeek.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * 입학 연도 vs 졸업 ABEEK 연도를 비교해 학생에게 적용할 "유리한" 최소 학점 요건.
 */
@Getter
@Builder
public class EffectiveAbeekRequirement {

    private final String departmentCode;
    private final int entranceYear;
    private final int graduationAbeekYear;

    private final int generalMinCredits;
    private final int bsmMinCredits;
    private final int majorMinCredits;
    private final double designMinCredits;

    private final int certElectiveMinCourses;
    private final int certElectiveMinCredits;
    private final int certElectiveMinAreas;

    /** 어떤 연도 값이 선택됐는지 설명 */
    private final String generalSource;
    private final String bsmSource;
    private final String majorSource;
    private final String designSource;
}
