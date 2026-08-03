package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.service.SejongAbeekCourseCodeCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRoadmapCategoryTest {

    private final StudentRoadmapService service = new StudentRoadmapService(null, null, null, null, null);

    private static TimetableOffering offering(String openingDepartment, String category, String courseName) {
        return new TimetableOffering(
                null, openingDepartment, "000001", "01", courseName, category,
                "1", 3.0, null, null, null, null, null
        );
    }

    @Test
    void normalizesLiberalAliasesToGeneralRequired() {
        assertThat(service.normalizeDisplayCategory("공필", "글쓰기")).isEqualTo("교양필수");
        assertThat(service.normalizeDisplayCategory("교선", "교양선택과목")).isEqualTo("교양필수");
        assertThat(service.normalizeDisplayCategory("균필", "균형교양")).isEqualTo("교양필수");
        assertThat(service.normalizeDisplayCategory("교양", "일반교양")).isEqualTo("교양필수");
    }

    @Test
    void classifiesNonMajorNonBsmAsGeneral() {
        assertThat(service.classifyAbeekBucket("교양필수", "글쓰기")).isEqualTo("GENERAL");
        assertThat(service.classifyAbeekBucket("교선", "영화의이해")).isEqualTo("GENERAL");
        assertThat(service.classifyAbeekBucket("미분류", "특강")).isEqualTo("GENERAL");
        assertThat(service.classifyAbeekBucket("전공필수", "자료구조")).isEqualTo("MAJOR");
        assertThat(service.classifyAbeekBucket("전선", "운영체제")).isEqualTo("MAJOR");
        assertThat(service.classifyAbeekBucket("기초필수", "미적분학1")).isEqualTo("BSM");
        assertThat(service.classifyAbeekBucket("전공기초", "이산수학")).isEqualTo("BSM");
    }

    @Test
    void abeekBsmAllowlistRejectsSiblingNumberedCourses() {
        var allow = new StudentRoadmapService.AbeekBsmAllowlist(
                Set.of("미적분학1", "일반물리학1", "공업수학1", "선형대수"),
                Set.of("BSM_CALC1", "BSM_PHYS", "BSM_EMATH1", "BSM_LINEAR"),
                new SejongAbeekCourseCodeCatalog()
        );
        assertThat(allow.matches(null, "미적분학1")).isTrue();
        assertThat(allow.matches(null, "미적분학2")).isFalse();
        assertThat(allow.matches(null, "일반물리학1")).isTrue();
        assertThat(allow.matches(null, "일반물리학2")).isFalse();
        assertThat(allow.matches(null, "일반화학1")).isFalse();
        assertThat(allow.matches(null, "일반생물학")).isFalse();
    }

    @Test
    void cse2021AllowlistRejectsSchoolWideCalc1AndPhysics1() {
        // CSE 2021 정본: 기초미적분학, 일반물리학및실험1 (미적분학1·일반물리학1 아님)
        var allow = new StudentRoadmapService.AbeekBsmAllowlist(
                Set.of("기초미적분학", "일반물리학및실험1", "공업수학1", "이산수학및프로그래밍",
                        "확률통계및프로그래밍", "선형대수및프로그래밍",
                        // 구·신 표기 alias만 허용
                        "확률및통계", "선형대수"),
                Set.of("BSM_CALC", "BSM_PHYS_LAB", "BSM_EMATH1", "BSM_DISC",
                        "BSM_PROB_PROG", "BSM_PROB", "BSM_LINEAR_PROG", "BSM_LINEAR"),
                new SejongAbeekCourseCodeCatalog()
        );
        assertThat(allow.matches(null, "기초미적분학")).isTrue();
        assertThat(allow.matches(null, "일반물리학및실험1")).isTrue();
        assertThat(allow.matches(null, "미적분학1")).isFalse();
        assertThat(allow.matches(null, "일반물리학1")).isFalse();
        assertThat(allow.matches("001357", "미적분학1")).isFalse();
        assertThat(allow.matches("002638", "일반물리학1")).isFalse();
        assertThat(allow.matches("006098", "기초미적분학")).isTrue();
        assertThat(allow.matches("002647", "일반물리학및실험1")).isTrue();
        assertThat(allow.matches(null, "선형대수")).isTrue();
        assertThat(allow.matches(null, "확률및통계")).isTrue();
    }

    @Test
    void cse2024AllowlistAcceptsCalc1AndPhysics1() {
        var allow = new StudentRoadmapService.AbeekBsmAllowlist(
                Set.of("미적분학1", "일반물리학1", "공업수학1", "이산수학및프로그래밍",
                        "확률및통계", "선형대수"),
                Set.of("BSM_CALC1", "BSM_PHYS", "BSM_EMATH1", "BSM_DISC", "BSM_PROB", "BSM_LINEAR"),
                new SejongAbeekCourseCodeCatalog()
        );
        assertThat(allow.matches("001357", "미적분학1")).isTrue();
        assertThat(allow.matches("002638", "일반물리학1")).isTrue();
        assertThat(allow.matches(null, "기초미적분학")).isFalse();
        assertThat(allow.matches(null, "일반물리학및실험1")).isFalse();
    }

    @Test
    void nonAbeekDoesNotInjectCampusStemFoundation() {
        Set<String> filmOpenings = Set.of("영화예술학과");
        TimetableOffering physics = offering("대양휴머니티칼리지", "기초필수", "일반물리학1");
        TimetableOffering calc = offering("대양휴머니티칼리지", "기초필수", "미적분학1");
        TimetableOffering filmMajor = offering("영화예술학과", "전공필수", "영화제작실습1");

        assertThat(service.shouldIncludeOffering(physics, filmOpenings, false)).isFalse();
        assertThat(service.shouldIncludeOffering(calc, filmOpenings, false)).isFalse();
        assertThat(service.shouldIncludeOffering(filmMajor, filmOpenings, false)).isTrue();
    }

    @Test
    void abeekStillInjectsCampusFoundationForBsmFiltering() {
        Set<String> cseOpenings = Set.of("컴퓨터공학과");
        TimetableOffering physics = offering("대양휴머니티칼리지", "기초필수", "일반물리학1");
        TimetableOffering cseMajor = offering("컴퓨터공학과", "전공필수", "자료구조");

        assertThat(service.shouldIncludeOffering(physics, cseOpenings, true)).isTrue();
        assertThat(service.shouldIncludeOffering(cseMajor, cseOpenings, true)).isTrue();
        assertThat(service.isCommonRequiredOffering(physics)).isTrue();
    }
}
