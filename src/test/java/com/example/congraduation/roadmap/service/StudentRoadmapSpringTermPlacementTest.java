package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 timetable-data JSON으로 1학기(gy=4→4-1) CSE 과목이 살아있는지 검증.
 */
class StudentRoadmapSpringTermPlacementTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<TimetableTermData> terms = new ArrayList<>();

    @BeforeEach
    void loadClasspathTimetables() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:timetable-data/*.json");
        for (Resource resource : resources) {
            terms.add(objectMapper.readValue(resource.getInputStream(), TimetableTermData.class));
        }
        terms.sort((a, b) -> {
            int byYear = Integer.compare(b.termYear(), a.termYear());
            if (byYear != 0) {
                return byYear;
            }
            return Integer.compare(b.semester(), a.semester());
        });
    }

    @Test
    void classpathHas2026SpringAndFall() {
        assertThat(terms.stream().anyMatch(t -> t.termYear() == 2026 && t.semester() == 1)).isTrue();
        assertThat(terms.stream().anyMatch(t -> t.termYear() == 2026 && t.semester() == 2)).isTrue();
    }

    @Test
    void latestSpringAndFallPlaceCseYear4Into41And42() {
        TimetableTermData spring = terms.stream()
                .filter(t -> t.semester() == 1)
                .findFirst()
                .orElseThrow();
        TimetableTermData fall = terms.stream()
                .filter(t -> t.semester() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(spring.termYear()).isEqualTo(2026);
        assertThat(spring.semester()).isEqualTo(1);
        assertThat(fall.termYear()).isEqualTo(2026);
        assertThat(fall.semester()).isEqualTo(2);

        Map<String, Map<String, String>> byTerm = new LinkedHashMap<>();
        for (String key : List.of("1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2")) {
            byTerm.put(key, new LinkedHashMap<>());
        }

        for (TimetableTermData term : List.of(spring, fall)) {
            for (TimetableOffering offering : term.offerings()) {
                String dept = offering.openingDepartment() == null ? "" : offering.openingDepartment();
                if (!dept.contains("컴퓨터공학")) {
                    continue;
                }
                Integer gy = parseGradeYear(offering.gradeYear());
                if (gy == null || gy < 1 || gy > 4) {
                    continue;
                }
                String code = offering.courseCode() == null ? "" : offering.courseCode().trim();
                if (code.isBlank()) {
                    continue;
                }
                String termKey = gy + "-" + term.semester();
                byTerm.get(termKey).putIfAbsent(code, offering.courseName());
            }
        }

        Set<String> fourOne = byTerm.get("4-1").keySet();
        assertThat(fourOne)
                .as("2026-1 CSE gy=4 must land in 4-1")
                .contains("006132", "010111", "011924", "009960", "012001");
        assertThat(byTerm.get("4-1").get("006132")).contains("영상처리");
        assertThat(byTerm.get("4-1").get("010111")).contains("졸업연구및진로1");
        assertThat(byTerm.get("4-1").get("011924")).contains("최신기술콜로키움3");

        assertThat(byTerm.get("4-2").keySet())
                .contains("010112", "011926", "009960");
    }

    private static Integer parseGradeYear(String gradeYear) {
        if (gradeYear == null || gradeYear.isBlank()) {
            return null;
        }
        String digits = gradeYear.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        return Integer.parseInt(digits.substring(0, 1));
    }
}
