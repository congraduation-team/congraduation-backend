package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.ClassicReadingCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassicReadingCertificationServiceTest {

    private final ClassicReadingCertificationService service = new ClassicReadingCertificationService();

    @Test
    void completesWhenClassicSpecialLectureWasTaken() {
        Student student = Student.create("21000001", "테스트", "경영학부", MajorType.SINGLE, null, 4, 2021, "ACTIVE", false);
        CompletedCourseUploadRowDto course = new CompletedCourseUploadRowDto(
                "2026",
                "2학기",
                "GEN_CLASSIC",
                "고전특강",
                "교선",
                "1",
                "GRADE",
                "P",
                "0"
        );

        ClassicReadingCertificationProgressDto result = service.evaluate(student, List.of(course));

        assertTrue(result.applicable());
        assertTrue(result.satisfied());
        assertEquals("COMPLETED", result.status());
        assertTrue(result.detail().contains("고전특강"));
    }

    @Test
    void marksCertifiedWhenCrawledClassicReadingWasCompleted() {
        Student student = Student.create("21000002", "테스트", "경영학부", MajorType.SINGLE, null, 4, 2021, "ACTIVE", false);
        student.updateClassicReadingCertificationInfo(true, 12, 10, 10);

        ClassicReadingCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.satisfied());
        assertEquals("CERTIFIED", result.status());
        assertTrue(result.detail().contains("인증 10권 / 필요 10권"));
    }

    @Test
    void staysInProgressWhenNeitherClassicLectureNorCertificationExists() {
        Student student = Student.create("24000001", "테스트", "경영학부", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);

        ClassicReadingCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.applicable());
        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
    }
}
