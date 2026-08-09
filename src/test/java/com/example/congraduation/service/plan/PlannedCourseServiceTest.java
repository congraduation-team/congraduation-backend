package com.example.congraduation.service.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
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
    void getPlannedCoursesForReadOnlyDoesNotCreateSemestersEvenWhenTranscriptExists() {
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
        when(plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(plannedSemesterRepository.findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());

        PlannedCourseListResponseDto result = service.getPlannedCoursesForReadOnly(1L);

        assertTrue(result.semesters().isEmpty());
        assertEquals("0", result.totalPlannedCredits());
        verify(plannedSemesterRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getPlannedCoursesStillCreatesSemestersWhenTranscriptExists() {
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
        when(transcriptStorageService.hasTranscript(1L)).thenReturn(true);
        when(plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(plannedSemesterRepository.findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(plannedSemesterRepository.findTopByStudentIdAndGradeYearAndSemesterOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(Optional.empty());
        when(plannedSemesterRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.getPlannedCourses(1L);

        verify(transcriptStorageService, atLeastOnce()).hasTranscript(1L);
        verify(plannedSemesterRepository, atLeastOnce()).save(org.mockito.ArgumentMatchers.any());
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

    private static void assignId(Student student, Long id) {
        try {
            Field field = Student.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
