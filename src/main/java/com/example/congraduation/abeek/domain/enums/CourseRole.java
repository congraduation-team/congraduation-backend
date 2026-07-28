package com.example.congraduation.abeek.domain.enums;

/**
 * 해당 연도 공학인증/교과과정에서 과목의 역할
 */
public enum CourseRole {
    /** 인증필수 (교양/전공 필수) */
    REQUIRED,
    /** 인증선택 (영역별 선택) */
    CERT_ELECTIVE,
    /** 전공선택 등 일반 선택 */
    ELECTIVE,
    /** BSM 지정 과목 */
    BSM_REQUIRED
}
