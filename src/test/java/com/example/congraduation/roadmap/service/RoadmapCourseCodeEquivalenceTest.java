package com.example.congraduation.roadmap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapCourseCodeEquivalenceTest {

    @Test
    void treatsProbAndLinearCurriculumRenameAsEquivalent() {
        assertThat(RoadmapCourseCodeEquivalence.canonical("009959"))
                .isEqualTo(RoadmapCourseCodeEquivalence.canonical("007330"));
        assertThat(RoadmapCourseCodeEquivalence.canonical("009961"))
                .isEqualTo(RoadmapCourseCodeEquivalence.canonical("001725"));

        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("007330"))
                .containsExactlyInAnyOrder(
                        RoadmapCourseCodeEquivalence.normalize("009959"),
                        RoadmapCourseCodeEquivalence.normalize("007330")
                );
        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("001725"))
                .containsExactlyInAnyOrder(
                        RoadmapCourseCodeEquivalence.normalize("009961"),
                        RoadmapCourseCodeEquivalence.normalize("001725")
                );
    }

    @Test
    void unknownCodeIsOnlyItself() {
        assertThat(RoadmapCourseCodeEquivalence.equivalentsIncludingSelf("000304"))
                .containsExactly(RoadmapCourseCodeEquivalence.normalize("000304"));
    }
}
