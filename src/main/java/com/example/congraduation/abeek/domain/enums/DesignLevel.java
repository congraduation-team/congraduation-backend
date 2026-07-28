package com.example.congraduation.abeek.domain.enums;

/**
 * 설계교과목 단계
 * <p>
 * 이수 순서: BASIC → ELEMENT → COMPREHENSIVE
 * 기초+요소, 요소+종합 병수 가능.
 * 기초 이수 전 / 종합 이수 후 수강한 요소설계는 설계학점 불인정.
 */
public enum DesignLevel {
    NONE,
    BASIC,
    ELEMENT,
    COMPREHENSIVE
}
