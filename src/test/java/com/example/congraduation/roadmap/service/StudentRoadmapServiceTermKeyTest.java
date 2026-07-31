package com.example.congraduation.roadmap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRoadmapServiceTermKeyTest {

    private final StudentRoadmapService service =
            new StudentRoadmapService(null, null, null);

    @Test
    void resolveTakenTermKey_mapsCalendarTermToStanding() {
        assertThat(service.resolveTakenTermKey(2021, "2023", "2학기")).isEqualTo("3-2");
        assertThat(service.resolveTakenTermKey(2021, "2024", "1학기")).isEqualTo("4-1");
        assertThat(service.resolveTakenTermKey(2021, "2025", "2학기")).isEqualTo("4-2");
    }

    @Test
    void resolveTakenTermKey_summerMapsToSemester1() {
        assertThat(service.resolveTakenTermKey(2021, "2023", "여름학기")).isEqualTo("3-1");
    }
}
