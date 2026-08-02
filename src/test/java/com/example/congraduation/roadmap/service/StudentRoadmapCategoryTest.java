package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.service.SejongAbeekCourseCodeCatalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRoadmapCategoryTest {

    private final StudentRoadmapService service = new StudentRoadmapService(null, null, null, null, null);

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
}
