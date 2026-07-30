package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.SwCodingCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwCodingCertificationServiceTest {

    private final SwCodingCertificationService service = new SwCodingCertificationService();

    @Test
    void returnsNotApplicableForStudentsBefore2023() {
        Student student = Student.create("22000001", "테스트", "컴퓨터공학과", MajorType.SINGLE, null, 4, 2022, "ACTIVE", false);

        SwCodingCertificationProgressDto result = service.evaluate(student, List.of());

        assertFalse(result.applicable());
        assertFalse(result.satisfied());
        assertEquals("NOT_APPLICABLE", result.status());
    }

    @Test
    void completesForMajorStudentWithAdvancedCAtB0OrHigher() {
        Student student = Student.create("24000001", "테스트", "컴퓨터공학과", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);
        CompletedCourseUploadRowDto course = new CompletedCourseUploadRowDto(
                "2026",
                "2학기",
                "MAJ_ADV_C",
                "고급C프로그래밍및실습",
                "전필",
                "3",
                "GRADE",
                "B0",
                "3.0"
        );

        SwCodingCertificationProgressDto result = service.evaluate(student, List.of(course));

        assertTrue(result.applicable());
        assertTrue(result.satisfied());
        assertEquals("COMPLETED", result.status());
        assertEquals("MAJOR", result.studentGroup());
    }

    @Test
    void staysInProgressForMajorStudentWhenAdvancedCGradeIsTooLow() {
        Student student = Student.create("24000001", "테스트", "컴퓨터공학과", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);
        CompletedCourseUploadRowDto course = new CompletedCourseUploadRowDto(
                "2026",
                "2학기",
                "MAJ_ADV_C",
                "고급C프로그래밍및실습",
                "전필",
                "3",
                "GRADE",
                "C+",
                "2.5"
        );

        SwCodingCertificationProgressDto result = service.evaluate(student, List.of(course));

        assertTrue(result.applicable());
        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
    }

    @Test
    void completesForNonMajorStudentWithCodingStorytellingCourse() {
        Student student = Student.create("24000002", "테스트", "경영학부", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);
        CompletedCourseUploadRowDto course = new CompletedCourseUploadRowDto(
                "2026",
                "2학기",
                "GEN_SW_001",
                "K-MOOC:코딩과스토리텔링",
                "교선",
                "1",
                "P/NP",
                "P",
                "0"
        );

        SwCodingCertificationProgressDto result = service.evaluate(student, List.of(course));

        assertTrue(result.applicable());
        assertTrue(result.satisfied());
        assertEquals("COMPLETED", result.status());
        assertEquals("NON_MAJOR", result.studentGroup());
    }
}
