package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.EnglishCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnglishCertificationServiceTest {

    private final EnglishCertificationService service = new EnglishCertificationService();

    @Test
    void exemptsArtsCollegeStudents() {
        Student student = Student.create("22000001", "테스트", "영화예술학과", MajorType.SINGLE, null, 4, 2022, "ACTIVE", false);

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.satisfied());
        assertEquals("EXEMPTED", result.status());
        assertFalse(result.applicable());
    }

    @Test
    void exemptsStudentsWhoCompletedIntensiveEnglish() {
        Student student = Student.create("22000002", "테스트", "경영학부", MajorType.SINGLE, null, 4, 2022, "ACTIVE", false);
        CompletedCourseUploadRowDto course = new CompletedCourseUploadRowDto(
                "2025",
                "1학기",
                "GEN_ENG_INT",
                "Intensive English",
                "교선",
                "3",
                "GRADE",
                "B0",
                "3.0"
        );

        EnglishCertificationProgressDto result = service.evaluate(student, List.of(course));

        assertTrue(result.satisfied());
        assertEquals("EXEMPTED", result.status());
        assertTrue(result.applicable());
    }

    @Test
    void exemptsStudentsAfterTenSemesters() {
        Student student = Student.create("17000001", "테스트", "경영학부", MajorType.SINGLE, null, 5, 2017, "ACTIVE", false);
        List<CompletedCourseUploadRowDto> courses = List.of(
                new CompletedCourseUploadRowDto("2021", "2학기", "A", "과목A", "교선", "3", "GRADE", "B0", "3.0")
        );

        EnglishCertificationProgressDto result = service.evaluate(student, courses);

        assertTrue(result.satisfied());
        assertEquals("EXEMPTED", result.status());
    }

    @Test
    void staysInProgressWithoutDetectableExemptionOrSubstitute() {
        Student student = Student.create("24000001", "테스트", "경영학부", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
        assertTrue(result.applicable());
    }
}
