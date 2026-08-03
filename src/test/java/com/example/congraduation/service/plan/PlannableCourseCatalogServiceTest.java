package com.example.congraduation.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.plan.PlannableCourseCatalogResponseDto;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.graduation.DepartmentCurriculumPolicyService;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlannableCourseCatalogServiceTest {

    @Test
    void returnsOnlyLatestSpringCoursesWhenSemesterIsOne() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(offering("100001", "알고리즘", "전공필수"))
        );
        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(offering("100002", "컴퓨터네트워크", "전공선택"))
        );

        when(timetableCatalog.latestTermForSemester(1)).thenReturn(Optional.of(spring2026));
        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026, fall2025));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                null,
                null,
                1,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsExactly("알고리즘");
        assertThat(response.courses().getFirst().offeredTerms()).containsExactly("2026-1");
    }

    @Test
    void returnsOnlyExactTermWhenOfferedTermIsProvided() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(offering("100002", "컴퓨터네트워크", "전공선택"))
        );

        when(timetableCatalog.findTerm(2025, 2)).thenReturn(Optional.of(fall2025));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                null,
                null,
                1,
                "2025-2",
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses().getFirst().courseName()).isEqualTo("컴퓨터네트워크");
        assertThat(response.courses().getFirst().offeredTerms()).containsExactly("2025-2");
    }

    @Test
    void rejectsUnsupportedSemesterValue() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        assertThatThrownBy(() -> service.getCatalog(
                null,
                null,
                null,
                3,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semester는 1 또는 2만 가능합니다.");
    }

    @Test
    void crossMajorRequiredAndElectiveBecomeMajorElectiveForAiConvergenceCollegeStudents() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공선택", "지능기전공학과")
                )
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "정보보호학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses()).extracting(course -> course.category())
                .containsExactly("전공선택");
    }

    @Test
    void crossMajorRequiredWithoutRecognitionPolicyBecomesLiberalArts() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공선택", "지능기전공학과")
                )
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "경영학부",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses()).extracting(course -> course.category())
                .containsExactly("교양");
    }

    @Test
    void keywordSearchReturnsOnlyMatchingCourses() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(
                        offering("100001", "자료구조및알고리즘", "전공필수"),
                        offering("100002", "컴퓨터네트워크", "전공선택"),
                        offering("100003", "Capstone디자인(산학협력프로젝트)", "전공선택")
                )
        );

        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                "알고리즘",
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsExactly("자료구조및알고리즘");
    }

    @Test
    void repeatedKeywordSearchDoesNotLeakPreviousResults() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009954", "알고리즘및실습", "전공필수", "컴퓨터공학과"),
                        offering("011495", "알고리즘및실습", "전공선택", "AI로봇학과")
                )
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto capstoneResponse = service.getCatalog(
                null,
                "cap",
                null,
                2,
                null,
                null,
                null
        );
        PlannableCourseCatalogResponseDto algorithmResponse = service.getCatalog(
                null,
                "알고리즘",
                null,
                2,
                null,
                null,
                null
        );

        assertThat(capstoneResponse.courses()).extracting(course -> course.courseName())
                .containsExactly("Capstone디자인(산학협력프로젝트)");
        assertThat(algorithmResponse.courses()).extracting(course -> course.courseName())
                .containsExactly("알고리즘및실습", "알고리즘및실습");
        assertThat(algorithmResponse.courses()).extracting(course -> course.category())
                .containsExactly("전공선택", "전공필수");
    }

    @Test
    void collapsesDuplicateRowsWithSameCourseCodeAndResolvedCategory() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(
                        offering("009960", ".(산학협력프로젝트)", "전공선택", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공선택", "지능기전공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공선택", "소프트웨어학과")
                )
        );

        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses().getFirst().courseName()).isEqualTo("Capstone디자인(산학협력프로젝트)");
        assertThat(response.courses().getFirst().category()).isEqualTo("전공선택");
        assertThat(response.courses().getFirst().courseCodes()).containsExactly("009960");
        assertThat(response.courses().getFirst().departments())
                .containsExactlyInAnyOrder("컴퓨터공학과", "지능기전공학과", "소프트웨어학과");
    }

    @Test
    void collapsesCapstoneIntoOnlyRequiredAndElectiveAcrossTerms() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "소프트웨어학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공선택", "지능기전공학과")
                )
        );
        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(
                        offering("009960", ".(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "정보보호학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공선택", "지능기전공학과")
                )
        );

        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026, fall2025));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                "cap",
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.courses()).extracting(course -> course.category())
                .containsExactly("전공선택", "전공필수");
        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsOnly("Capstone디자인(산학협력프로젝트)");
        assertThat(response.courses().get(0).offeredTerms()).containsExactlyInAnyOrder("2025-2", "2026-1");
        assertThat(response.courses().get(1).offeredTerms()).containsExactlyInAnyOrder("2025-2", "2026-1");
    }

    @Test
    void splitsMajorRequiredCourseIntoRequiredAndElectiveByDepartmentBeforeDisplayMerge() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "컴퓨터공학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "소프트웨어학과"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "지능기전공학부 무인이동체공학전공"),
                        offering("009960", "Capstone디자인(산학협력프로젝트)", "전공필수", "지능기전공학부 스마트기기공학전공")
                )
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                "cap",
                null,
                2,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses()).extracting(course -> course.category())
                .containsExactly("전공필수");
        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsOnly("Capstone디자인(산학협력프로젝트)");
        assertThat(response.courses().getFirst().offeredTerms()).containsExactly("2025-2");
    }

    @Test
    void majorElectiveCategoryFilterReturnsOnlyOwnDepartmentElectives() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("009955", "이산수학및프로그래밍", "전공선택", "컴퓨터공학과"),
                        offering("011495", "알고리즘및실습", "전공선택", "AI로봇학과"),
                        offering("010418", "자기주도창의전공Ⅰ", "전공선택", "교양대학")
                )
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                null,
                null,
                2,
                null,
                null,
                "전공선택"
        );

        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsExactlyInAnyOrder("이산수학및프로그래밍", "알고리즘및실습", "자기주도창의전공Ⅰ");
        assertThat(response.courses()).extracting(course -> course.departments())
                .allMatch(departments -> departments.contains("컴퓨터공학과")
                        || departments.contains("AI로봇학과")
                        || departments.contains("교양대학"));
    }

    @Test
    void majorElectiveFilterKeepsCrossDepartmentElectiveWhenCourseCodeMatchesOwnMajorElective() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("006237", "웹프로그래밍", "전공선택", "컴퓨터공학과"),
                        offering("006237", "웹프로그래밍", "전공선택", "경영학과"),
                        offering("010418", "자기주도창의전공Ⅰ", "전공선택", "교양대학")
                )
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                null,
                null,
                2,
                null,
                null,
                "전공선택"
        );

        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsExactlyInAnyOrder("웹프로그래밍", "자기주도창의전공Ⅰ");
        assertThat(response.courses().stream()
                .filter(course -> course.courseName().equals("웹프로그래밍"))
                .findFirst()
                .orElseThrow()
                .departments()).containsExactlyInAnyOrder("컴퓨터공학과", "경영학과");
    }

    @Test
    void majorFoundationOnlyCourseDisplaysAsMajorFoundation() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(offering("000137", "경영학원론", "전공기초", "경영학부"))
        );

        Student student = Student.create(
                "26000001",
                "테스트학생",
                "경영학부",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                "경영학원론",
                null,
                2,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses().getFirst().category()).isEqualTo("전공기초");
    }

    @Test
    void crossMajorFoundationWithoutOwnEquivalentBecomesLiberalArts() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(offering("000137", "경영학원론", "전공기초", "경영학부"))
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                "경영학원론",
                null,
                2,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses().getFirst().category()).isEqualTo("교양");
    }

    @Test
    void crossMajorFoundationWithSameOwnCourseCodeStaysMajorFoundation() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("000137", "경영학원론", "전공기초", "경영학부"),
                        offering("000137", "경영학원론", "전공기초", "경제학과")
                )
        );

        Student student = Student.create(
                "26000001",
                "테스트학생",
                "경영학부",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                "경영학원론",
                null,
                2,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses().getFirst().category()).isEqualTo("전공기초");
    }

    @Test
    void majorFoundationFilterIncludesDualCountMajorRequiredCourse() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);
        DepartmentCurriculumPolicyService departmentCurriculumPolicyService = new DepartmentCurriculumPolicyService();

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(
                        offering("007313", "공업수학1", "전공필수", "컴퓨터공학과"),
                        offering("006237", "웹프로그래밍", "전공선택", "컴퓨터공학과")
                )
        );

        Student student = Student.create(
                "24000001",
                "테스트학생",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2024,
                "재학",
                false
        );

        when(timetableCatalog.latestTermForSemester(2)).thenReturn(Optional.of(fall2025));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService,
                departmentCurriculumPolicyService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                1L,
                null,
                null,
                2,
                null,
                null,
                "전기"
        );

        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsExactly("공업수학1");
        assertThat(response.courses().getFirst().category()).isEqualTo("전공필수");
    }

    private TimetableOffering offering(String courseCode, String courseName, String category) {
        return offering(courseCode, courseName, category, "컴퓨터공학과");
    }

    private TimetableOffering offering(String courseCode, String courseName, String category, String hostDepartment) {
        return new TimetableOffering(
                "공과대학",
                hostDepartment,
                courseCode,
                "001",
                courseName,
                category,
                "3",
                3.0,
                "이론",
                "월 09:00~10:30",
                "집현관",
                "홍길동",
                hostDepartment
        );
    }
}
