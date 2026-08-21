package com.example.congraduation.roadmap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapCourseCodeEquivalenceTest {

    @Test
    void treatsProbAndLinearCurriculumRenameAsEquivalent() {
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("009959", "007330")).isTrue();
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("009961", "001725")).isTrue();

        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("007330"))
                .contains(
                        RoadmapCourseCodeEquivalence.normalize("009959"),
                        RoadmapCourseCodeEquivalence.normalize("007330")
                );
        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("001725"))
                .contains(
                        RoadmapCourseCodeEquivalence.normalize("009961"),
                        RoadmapCourseCodeEquivalence.normalize("001725")
                );
    }

    @Test
    void treatsSeminarAndCampusLifeRenamesAsEquivalent() {
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("011110", "011614")).isTrue();
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("011182", "011839")).isTrue();
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("011110", "011182")).isFalse();
        assertThat(RoadmapCourseCodeEquivalence.namesShareGroup("신입생세미나", "세종인을위한진로설계")).isTrue();
        assertThat(RoadmapCourseCodeEquivalence.namesShareGroup("대학생활과진로탐색", "세종인을위한전공탐색")).isTrue();
    }

    @Test
    void overlappingGroupsAreNotTransitive() {
        // 007330은 확률통계및프로그래밍 그룹과 데이터과학과확률 그룹에 모두 있다.
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("007330", "009959")).isTrue();
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("007330", "012262")).isTrue();
        assertThat(RoadmapCourseCodeEquivalence.shareGroup("009959", "012262")).isFalse();
        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("009959"))
                .doesNotContain(RoadmapCourseCodeEquivalence.normalize("012262"));
    }

    @Test
    void unknownCodeIsOnlyItself() {
        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("NO_SUCH_COURSE_ZZ"))
                .containsExactly(RoadmapCourseCodeEquivalence.normalize("NO_SUCH_COURSE_ZZ"));
    }
}
