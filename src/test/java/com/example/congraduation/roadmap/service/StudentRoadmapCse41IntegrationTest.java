package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.abeek.service.AbeekDepartmentCatalog;
import com.example.congraduation.abeek.service.SejongAbeekCourseCodeCatalog;
import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * classpath 시간표 + StudentRoadmapService 로 CSE 4-1 과목 존재 여부 검증.
 */
class StudentRoadmapCse41IntegrationTest {

    @TempDir
    Path emptyOverrideDir;

    private StudentRoadmapService service;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TimetableCatalog catalog = new TimetableCatalog(objectMapper, emptyOverrideDir.toString());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(catalog, "load");

        service = new StudentRoadmapService(
                catalog,
                new AbeekDepartmentCatalog(),
                mock(TranscriptStorageService.class),
                mock(CurriculumCourseRepository.class),
                new SejongAbeekCourseCodeCatalog()
        );
    }

    @Test
    void cseRoadmapKeepsSpringOnlyFourthYearCoursesIn41() {
        StudentRoadmapResponse response = service.getByDepartment("컴퓨터공학과", null);

        assertThat(response.getSourceTerms())
                .anyMatch(t -> t.getTermYear() == 2026 && t.getSemester() == 1 && t.getOfferingCount() > 0);
        assertThat(response.getSourceTerms())
                .anyMatch(t -> t.getTermYear() == 2026 && t.getSemester() == 2 && t.getOfferingCount() > 0);

        StudentRoadmapResponse.TermRoadmapDto term41 = response.getTerms().stream()
                .filter(t -> "4-1".equals(t.getTermKey()))
                .findFirst()
                .orElseThrow();

        Set<String> codes = term41.getCourses().stream()
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseCode)
                .collect(Collectors.toSet());
        Set<String> names = term41.getCourses().stream()
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(codes)
                .as("4-1 must include spring-only CSE courses from 2026-1")
                .contains("006132", "010111", "011924");
        assertThat(names).anyMatch(n -> n.contains("영상처리"));
        assertThat(names).anyMatch(n -> n.contains("졸업연구및진로1"));
        assertThat(names).anyMatch(n -> n.contains("최신기술콜로키움3"));

        // 입학연도 없이 커리큘럼 preferred가 없으면, 중복 학수번호는 더 이른 칸만 유지
        List<String> capstoneTerms = response.getTerms().stream()
                .filter(t -> t.getCourses().stream().anyMatch(c -> "009960".equals(c.getCourseCode())))
                .map(StudentRoadmapResponse.TermRoadmapDto::getTermKey)
                .toList();
        assertThat(capstoneTerms).hasSize(1);
    }
}
