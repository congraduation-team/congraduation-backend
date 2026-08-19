package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.abeek.service.AbeekDepartmentCatalog;
import com.example.congraduation.abeek.service.SejongAbeekCourseCodeCatalog;
import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse;
import com.example.congraduation.service.graduation.AcademicFoundationCoursePolicyService;
import com.example.congraduation.service.graduation.BalancedLiberalCoursePolicyService;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
                new SejongAbeekCourseCodeCatalog(),
                new AcademicFoundationCoursePolicyService(),
                new BalancedLiberalCoursePolicyService()
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

        // 입학연도 없이 recommendedTerm이 없으면 양학기 개설 과목은 양쪽 칸 유지
        List<String> capstoneTerms = response.getTerms().stream()
                .filter(t -> t.getCourses().stream().anyMatch(c -> "009960".equals(c.getCourseCode())))
                .map(StudentRoadmapResponse.TermRoadmapDto::getTermKey)
                .toList();
        assertThat(capstoneTerms).contains("4-1", "4-2");
    }

    @Test
    void cseRoadmapPlacesFallOnlyCoursesInEvenTerms() {
        StudentRoadmapResponse response = service.getByDepartment("컴퓨터공학과", null);

        Map<String, Set<String>> codesByTerm = response.getTerms().stream()
                .collect(Collectors.toMap(
                        StudentRoadmapResponse.TermRoadmapDto::getTermKey,
                        t -> t.getCourses().stream()
                                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseCode)
                                .collect(Collectors.toSet())
                ));

        assertThat(codesByTerm.get("3-2"))
                .as("fall-only majors must land in 3-2")
                .contains("003284", "011920", "011923");
        assertThat(codesByTerm.get("4-2"))
                .as("fall-only majors must land in 4-2")
                .contains("011514", "011926");

        assertThat(codesByTerm.get("3-1")).doesNotContain("003284", "011920", "011923");
        assertThat(codesByTerm.get("4-1")).doesNotContain("011514", "011926");

        assertThat(codesByTerm.get("3-2")).isNotEmpty();
        assertThat(codesByTerm.get("4-2")).isNotEmpty();
        assertThat(codesByTerm.get("1-2")).isNotEmpty();
        assertThat(codesByTerm.get("2-2")).isNotEmpty();
    }

    @Test
    void nonAbeekDepartmentsDoNotGetSchoolWideStemFoundation() {
        for (String department : List.of("음악과", "영화예술학과", "회화과", "무용과")) {
            StudentRoadmapResponse response = service.getByDepartment(department, null);
            Set<String> names = response.getTerms().stream()
                    .flatMap(t -> t.getCourses().stream())
                    .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                    .collect(Collectors.toSet());

            assertThat(names)
                    .as(department + " must not include school-wide STEM 학문기초")
                    .noneMatch(n -> n != null && (n.contains("일반물리") || n.contains("미적분")
                            || n.contains("일반화학") || n.contains("일반생물") || n.contains("공업수학")));
        }
    }

    @Test
    void musicRoadmapShowsIncompleteRequiredCommonLiberalAndFoundation() {
        StudentRoadmapResponse response = service.getByDepartment("음악과", null);
        List<StudentRoadmapResponse.RoadmapCourseDto> courses = response.getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .toList();
        Set<String> names = courses.stream()
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(names).anyMatch(n -> n != null && n.contains("컴퓨터사고기반기초코딩"));
        assertThat(names).anyMatch(n -> n != null && n.contains("인공지능과빅데이터"));
        assertThat(names).anyMatch(n -> n != null && n.contains("비판적사고와창의적글쓰기"));
        assertThat(names).anyMatch(n -> n != null && n.contains("대학영어"));
        assertThat(names).anyMatch(n -> n != null && n.contains("세종인을위한진로설계"));
        assertThat(names).noneMatch(n -> n != null && n.contains("일반물리"));

        assertThat(courses)
                .filteredOn(c -> c.getCourseName() != null && c.getCourseName().contains("컴퓨터사고기반기초코딩"))
                .allMatch(c -> "기초필수".equals(c.getCategory()) && !c.isCompleted());
        assertThat(courses)
                .filteredOn(c -> c.getCourseName() != null && c.getCourseName().contains("대학영어"))
                .allMatch(c -> "교양필수".equals(c.getCategory()) && !c.isCompleted());
    }

    @Test
    void loggedInMusicStudentKeepsIncompleteRequiredSlots() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TimetableCatalog catalog = new TimetableCatalog(objectMapper, emptyOverrideDir.toString());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(catalog, "load");

        Student student = Student.create(
                "26010001",
                "테스트",
                "음악과",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "ACTIVE",
                false
        );
        org.springframework.test.util.ReflectionTestUtils.setField(student, "id", 1L);

        TranscriptStorageService transcripts = mock(TranscriptStorageService.class);
        org.mockito.Mockito.when(transcripts.getStudentOrThrow(1L)).thenReturn(student);
        org.mockito.Mockito.when(transcripts.getLatestTranscriptRows(1L)).thenReturn(List.of());

        StudentRoadmapService loggedInService = new StudentRoadmapService(
                catalog,
                new AbeekDepartmentCatalog(),
                transcripts,
                mock(CurriculumCourseRepository.class),
                new SejongAbeekCourseCodeCatalog(),
                new AcademicFoundationCoursePolicyService(),
                new BalancedLiberalCoursePolicyService()
        );

        StudentRoadmapResponse response = loggedInService.getByStudent(1L);
        Set<String> names = response.getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(names).anyMatch(n -> n != null && n.contains("컴퓨터사고기반기초코딩"));
        assertThat(names).anyMatch(n -> n != null && n.contains("인공지능과빅데이터"));
        assertThat(names).anyMatch(n -> n != null && n.contains("대학영어"));
        assertThat(names).anyMatch(n -> n != null && n.contains("비판적사고와창의적글쓰기"));
        assertThat(names).noneMatch(n -> n != null && (n.contains("English Listening") || n.contains("일반물리")));
    }

    @Test
    void cseStudentViewingMusicSeesMusicFoundationNotOwnCalc() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TimetableCatalog catalog = new TimetableCatalog(objectMapper, emptyOverrideDir.toString());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(catalog, "load");

        Student student = Student.create(
                "22010001",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2022,
                "ACTIVE",
                false
        );
        org.springframework.test.util.ReflectionTestUtils.setField(student, "id", 1L);

        TranscriptStorageService transcripts = mock(TranscriptStorageService.class);
        org.mockito.Mockito.when(transcripts.getStudentOrThrow(1L)).thenReturn(student);
        org.mockito.Mockito.when(transcripts.getLatestTranscriptRows(1L)).thenReturn(List.of(
                new com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto(
                        "2022", "1학기", "006098", "기초미적분학", "학문기초", "3", "GRADE", "A0", "4.0")
        ));

        StudentRoadmapService loggedInService = new StudentRoadmapService(
                catalog,
                new AbeekDepartmentCatalog(),
                transcripts,
                mock(CurriculumCourseRepository.class),
                new SejongAbeekCourseCodeCatalog(),
                new AcademicFoundationCoursePolicyService(),
                new BalancedLiberalCoursePolicyService()
        );

        StudentRoadmapResponse response = loggedInService.getByDepartment("음악과", 1L);
        List<StudentRoadmapResponse.RoadmapCourseDto> courses = response.getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .toList();

        assertThat(courses)
                .filteredOn(c -> c.getCourseName() != null && c.getCourseName().contains("컴퓨터사고기반기초코딩"))
                .isNotEmpty()
                .allMatch(c -> "기초필수".equals(c.getCategory()) && !c.isCompleted());
        assertThat(courses)
                .filteredOn(c -> c.getCourseName() != null && c.getCourseName().contains("인공지능과빅데이터"))
                .isNotEmpty()
                .allMatch(c -> "기초필수".equals(c.getCategory()));
        assertThat(courses)
                .filteredOn(c -> "기초미적분학".equals(c.getCourseName()))
                .noneMatch(StudentRoadmapResponse.RoadmapCourseDto::isCompleted);
    }

    @Test
    void renamedCommonLiberalCoursesUseCurrentTimetableNames() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TimetableCatalog catalog = new TimetableCatalog(objectMapper, emptyOverrideDir.toString());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(catalog, "load");

        Student student2021 = Student.create(
                "21010001",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2021,
                "ACTIVE",
                false
        );
        org.springframework.test.util.ReflectionTestUtils.setField(student2021, "id", 21L);
        Student student2022 = Student.create(
                "22010002",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2022,
                "ACTIVE",
                false
        );
        org.springframework.test.util.ReflectionTestUtils.setField(student2022, "id", 22L);

        TranscriptStorageService transcripts = mock(TranscriptStorageService.class);
        org.mockito.Mockito.when(transcripts.getStudentOrThrow(21L)).thenReturn(student2021);
        org.mockito.Mockito.when(transcripts.getLatestTranscriptRows(21L)).thenReturn(List.of());
        org.mockito.Mockito.when(transcripts.getStudentOrThrow(22L)).thenReturn(student2022);
        org.mockito.Mockito.when(transcripts.getLatestTranscriptRows(22L)).thenReturn(List.of());

        StudentRoadmapService loggedInService = new StudentRoadmapService(
                catalog,
                new AbeekDepartmentCatalog(),
                transcripts,
                mock(CurriculumCourseRepository.class),
                new SejongAbeekCourseCodeCatalog(),
                new AcademicFoundationCoursePolicyService(),
                new BalancedLiberalCoursePolicyService()
        );

        Set<String> names2021 = loggedInService.getByStudent(21L).getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());
        Set<String> names2022 = loggedInService.getByStudent(22L).getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(names2021).anyMatch(n -> n != null && n.contains("세종인을위한전공탐색"));
        assertThat(names2021).noneMatch(n -> n != null && n.contains("대학생활과진로탐색"));
        assertThat(names2022).anyMatch(n -> n != null && n.contains("세종인을위한진로설계"));
        assertThat(names2022).anyMatch(n -> n != null && n.contains("세종인을위한전공탐색"));
        assertThat(names2022).noneMatch(n -> n != null && n.contains("신입생세미나"));
    }

    @Test
    void lawMajorRoadmapIncludesLawDepartmentMajorCourses() {
        StudentRoadmapResponse response = service.getByDepartment("법학전공", null);
        Set<String> names = response.getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(names).anyMatch(n -> n != null && n.contains("법학입문"));
        assertThat(names).anyMatch(n -> n != null && n.contains("민법총칙"));
        assertThat(names).anyMatch(n -> n != null && n.contains("헌법1"));
        assertThat(names).anyMatch(n -> n != null && n.contains("행정법1"));
        assertThat(names).anyMatch(n -> n != null && n.contains("LEET언어이해심화"));
        assertThat(names).anyMatch(n -> n != null && n.contains("졸업연구및진로1"));
    }

    @Test
    void nanoRoadmapKeepsHandbookFoundationNotSchoolWideCatalog() {
        StudentRoadmapResponse response = service.getByDepartment("나노신소재공학과", null);
        Set<String> names = response.getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(names).anyMatch(n -> n != null && n.contains("미적분학1"));
        assertThat(names).anyMatch(n -> n != null && n.contains("공업수학1"));
        assertThat(names).anyMatch(n -> n != null && n.contains("일반물리"));
        assertThat(names).anyMatch(n -> n != null && n.contains("일반화학"));
        assertThat(names).noneMatch(n -> n != null && n.contains("SW기초코딩"));
        assertThat(names).noneMatch(n -> n != null && n.contains("고급프로그래밍활용"));
        assertThat(names).noneMatch(n -> n != null && n.contains("사회과학수학"));
        assertThat(names).noneMatch(n -> n != null && n.contains("일반생물"));
        assertThat(names).noneMatch(n -> n != null && n.contains("컴퓨터사고기반기초코딩"));
        assertThat(names).noneMatch(n -> n != null && n.contains("프로그래밍활용-P"));
        assertThat(names).noneMatch(n -> n != null && n.contains("프로그래밍활용-C"));
        assertThat(names).noneMatch(n -> n != null && n.contains("통계학개론"));
        assertThat(names).noneMatch(n -> n != null && n.contains("인공지능과빅데이터"));

        Map<String, Set<String>> namesByTerm = response.getTerms().stream()
                .collect(Collectors.toMap(
                        StudentRoadmapResponse.TermRoadmapDto::getTermKey,
                        term -> term.getCourses().stream()
                                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                                .filter(n -> n != null)
                                .collect(Collectors.toSet())
                ));
        assertThat(namesByTerm.getOrDefault("1-1", Set.of()))
                .anyMatch(n -> n.contains("미적분학1"))
                .anyMatch(n -> n.contains("일반물리학1"))
                .anyMatch(n -> n.contains("일반화학1"))
                .noneMatch(n -> n.contains("미적분학2"));
        assertThat(namesByTerm.getOrDefault("1-2", Set.of()))
                .anyMatch(n -> n.contains("미적분학2"))
                .anyMatch(n -> n.contains("일반물리학2"))
                .anyMatch(n -> n.contains("일반화학2"));
        assertThat(namesByTerm.getOrDefault("2-1", Set.of())).anyMatch(n -> n.contains("공업수학1"));
        assertThat(namesByTerm.getOrDefault("2-2", Set.of())).anyMatch(n -> n.contains("공업수학2"));
    }

    @Test
    void cseGeneralRoadmapDoesNotUseAbeekBsm() {
        StudentRoadmapResponse response = service.getByDepartment("컴퓨터공학과", null);
        List<StudentRoadmapResponse.RoadmapCourseDto> courses = response.getTerms().stream()
                .flatMap(t -> t.getCourses().stream())
                .toList();
        Set<String> names = courses.stream()
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());
        Set<String> foundationNames = courses.stream()
                .filter(c -> "기초필수".equals(c.getCategory()))
                .map(StudentRoadmapResponse.RoadmapCourseDto::getCourseName)
                .collect(Collectors.toSet());

        assertThat(names).anyMatch(n -> n != null && n.contains("미적분학1"));
        assertThat(names).anyMatch(n -> n != null && n.contains("고급프로그래밍활용"));
        assertThat(names).anyMatch(n -> n != null && n.contains("인공지능과빅데이터"));
        assertThat(foundationNames).noneMatch(n -> n != null && n.contains("일반물리"));
        assertThat(foundationNames).noneMatch(n -> n != null && n.contains("공업수학"));
        assertThat(foundationNames).noneMatch(n -> n != null && n.contains("일반화학"));
        assertThat(foundationNames).noneMatch(n -> n != null && n.contains("일반생물"));
        assertThat(courses)
                .filteredOn(c -> c.getCourseName() != null && c.getCourseName().contains("일반물리학1"))
                .allMatch(c -> "전공선택".equals(c.getCategory()) || "MAJOR".equals(c.getAbeekBucket()));
    }
}
