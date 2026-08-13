package com.example.congraduation.service.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.plan.PlannedCourse;
import com.example.congraduation.domain.plan.PlannedSemester;
import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.repository.plan.PlannedCourseRepository;
import com.example.congraduation.repository.plan.PlannedSemesterRepository;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlannedCourseServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PlannedCourseRepository plannedCourseRepository;

    @Mock
    private PlannedSemesterRepository plannedSemesterRepository;

    @Mock
    private TranscriptStorageService transcriptStorageService;

    @Test
    void getPlannedCoursesDoesNotCreateSemestersWhenTranscriptIsMissing() {
        PlannedCourseService service = new PlannedCourseService(
                studentRepository,
                plannedCourseRepository,
                plannedSemesterRepository,
                transcriptStorageService
        );

        Student student = Student.create(
                "24012357",
                "김정현",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );
        assignId(student, 1L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(transcriptStorageService.hasTranscript(1L)).thenReturn(false);
        when(plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(plannedSemesterRepository.findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());

        PlannedCourseListResponseDto result = service.getPlannedCourses(1L);

        assertTrue(result.semesters().isEmpty());
        assertEquals("0", result.totalPlannedCredits());
        verify(plannedSemesterRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void projectedRowsUseCalendarYearDerivedFromAdmissionYearAndGradeYear() {
        PlannedCourseService service = new PlannedCourseService(
                studentRepository,
                plannedCourseRepository,
                plannedSemesterRepository,
                transcriptStorageService
        );

        Student student = Student.create(
                "24012357",
                "김정현",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "재학",
                false
        );
        assignId(student, 1L);
        PlannedSemester plannedSemester = PlannedSemester.create(student, 3, 2);
        PlannedCourse plannedCourse = PlannedCourse.create(
                student,
                plannedSemester,
                3,
                2,
                "011317",
                "컴퓨터게임과메타버스",
                "교양",
                "3",
                "A0"
        );

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of(plannedCourse));

        List<CompletedCourseUploadRowDto> projectedRows = service.getProjectedRows(1L);

        assertEquals(1, projectedRows.size());
        assertEquals("2026", projectedRows.getFirst().year());
        assertEquals("2학기", projectedRows.getFirst().semester());
        assertEquals("컴퓨터게임과메타버스", projectedRows.getFirst().courseName());
    }

    @Test
    void officialCompletedSemestersOverridesInflatedTranscriptStanding() {
        PlannedCourseService service = new PlannedCourseService(
                studentRepository,
                plannedCourseRepository,
                plannedSemesterRepository,
                transcriptStorageService
        );

        // classic 이수 학기=5. 기이수는 군E러닝 2025-2까지 포함해 정규학기 6개로 보임.
        Student student = Student.create(
                "24012357",
                "김정현",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2023,
                "재학",
                false
        );
        student.updateAcademicInfo("김정현", "컴퓨터공학과", 3, 5, 2023, "재학");
        assignId(student, 1L);

        List<CompletedCourseUploadRowDto> rows = List.of(
                row("2023", "1학기", "A"),
                row("2023", "2학기", "B"),
                row("2024", "1학기", "C"),
                row("2024", "2학기", "D"),
                row("2025", "1학기", "E"),
                row("2025", "2학기", "MILITARY_E")
        );

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(transcriptStorageService.hasTranscript(1L)).thenReturn(true);
        when(transcriptStorageService.getLatestTranscriptRows(1L)).thenReturn(rows);
        when(plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(plannedSemesterRepository.findTopByStudentIdAndGradeYearAndSemesterOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(Optional.empty());
        when(plannedSemesterRepository.save(org.mockito.ArgumentMatchers.any(PlannedSemester.class)))
                .thenAnswer(invocation -> {
                    PlannedSemester semester = invocation.getArgument(0);
                    assignId(semester, (long) (semester.getGradeYear() * 10 + semester.getSemester()));
                    return semester;
                });
        when(plannedSemesterRepository.findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(1L))
                .thenAnswer(invocation -> List.of(
                        planned(student, 3, 2),
                        planned(student, 4, 1),
                        planned(student, 4, 2)
                ));

        PlannedCourseListResponseDto result = service.getPlannedCourses(1L);

        assertEquals("3-1", result.lastCompletedSemester());
        assertEquals("2025", result.lastCompletedTakenYear());
        assertEquals("1학기", result.lastCompletedTakenSemester());
        assertEquals(3, result.semesters().size());
        assertEquals(3, result.semesters().get(0).gradeYear());
        assertEquals(2, result.semesters().get(0).semester());
        verify(plannedSemesterRepository, org.mockito.Mockito.times(3))
                .save(org.mockito.ArgumentMatchers.any(PlannedSemester.class));
    }

    private static PlannedSemester planned(Student student, int gradeYear, int semester) {
        PlannedSemester plannedSemester = PlannedSemester.create(student, gradeYear, semester);
        assignId(plannedSemester, (long) (gradeYear * 10 + semester));
        return plannedSemester;
    }

    private static CompletedCourseUploadRowDto row(String year, String semester, String code) {
        return new CompletedCourseUploadRowDto(
                year, semester, code, "과목" + code, "전선", "3", "GRADE", "B0", "3.0"
        );
    }

    private static void assignId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
