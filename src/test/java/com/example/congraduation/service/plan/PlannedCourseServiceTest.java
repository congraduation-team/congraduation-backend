package com.example.congraduation.service.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.plan.PlannedCourse;
import com.example.congraduation.domain.plan.PlannedSemester;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.repository.plan.PlannedCourseRepository;
import com.example.congraduation.repository.plan.PlannedSemesterRepository;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.transcript.TranscriptStorageService;
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
}
