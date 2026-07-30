package com.example.congraduation.service.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.student.StudentMajorTrackRequestDto;
import com.example.congraduation.dto.student.StudentMajorTrackUpdateRequestDto;
import com.example.congraduation.repository.student.StudentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudentServiceTest {

    @Test
    void updateMajorTrackIgnoresSingleTrackFromFrontendPayload() {
        StudentRepository studentRepository = mock(StudentRepository.class);
        MajorCatalogService majorCatalogService = new MajorCatalogService();
        StudentService service = new StudentService(studentRepository, majorCatalogService);

        Student student = Student.create(
                "24000001",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2024,
                "ACTIVE",
                false
        );

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        StudentMajorTrackUpdateRequestDto request = new StudentMajorTrackUpdateRequestDto(
                MajorType.DOUBLE_MAJOR,
                "외식경영학전공",
                List.of(
                        new StudentMajorTrackRequestDto(MajorType.SINGLE, "컴퓨터공학과", null, false),
                        new StudentMajorTrackRequestDto(MajorType.DOUBLE_MAJOR, "외식경영학전공", null, false)
                )
        );

        Student updated = service.updateMajorTrack(1L, request);

        assertEquals(MajorType.DOUBLE, updated.getMajorType());
        assertEquals("외식경영학전공", updated.getSecondaryMajor());
        assertEquals(1, updated.getMajorTracks().size());
        assertEquals(MajorType.DOUBLE_MAJOR, updated.getMajorTracks().getFirst().getTrackType());
        assertEquals("외식경영학전공", updated.getMajorTracks().getFirst().getDepartmentCode());
        assertNotNull(updated.getCreatedAt());
    }
}
