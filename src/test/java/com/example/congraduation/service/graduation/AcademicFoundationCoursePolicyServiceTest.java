package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcademicFoundationCoursePolicyServiceTest {

    private final AcademicFoundationCoursePolicyService service = new AcademicFoundationCoursePolicyService();

    @Test
    void evaluateComputerScience2022TracksCompletedAndRemainingAcademicFoundationCourses() {
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

        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2022", "1학기", "A001", "고급프로그래밍활용", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2022", "2학기", "A002", "인공지능과빅데이터", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2022", "2학기", "A003", "공업수학1", "학문기초", "3", "GRADE", "A0", "4.0")
        );

        AcademicFoundationCoursePolicyService.AcademicFoundationEvaluation evaluation =
                service.evaluate(student, completedCourses);

        assertTrue(evaluation.policyApplied());
        assertEquals("9", evaluation.earnedCredits().stripTrailingZeros().toPlainString());
        assertEquals(15, evaluation.requiredCredits());
        assertEquals(3, evaluation.completedCourses().size());
        assertEquals(2, evaluation.remainingCourses().size());
        assertTrue(evaluation.remainingCourses().stream()
                .anyMatch(course -> "기초미적분학".equals(course.courseName())));
        assertTrue(evaluation.remainingCourses().stream()
                .anyMatch(course -> "일반물리학1".equals(course.courseName())));
    }

    @Test
    void evaluateChemistry2021AcceptsAlternativeCourseGroup() {
        Student student = Student.create(
                "21010002",
                "테스트",
                "화학과",
                MajorType.SINGLE,
                null,
                2,
                2021,
                "ACTIVE",
                false
        );

        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2021", "1학기", "B001", "일반수미적분학", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2021", "2학기", "B002", "다변수미적분학", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2021", "1학기", "B003", "일반화학및실험1", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2021", "2학기", "B004", "일반화학및실험2", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2021", "2학기", "B005", "통계학개론", "학문기초", "3", "GRADE", "A0", "4.0")
        );

        AcademicFoundationCoursePolicyService.AcademicFoundationEvaluation evaluation =
                service.evaluate(student, completedCourses);

        assertTrue(evaluation.policyApplied());
        assertEquals("15", evaluation.earnedCredits().stripTrailingZeros().toPlainString());
        assertEquals(15, evaluation.requiredCredits());
        assertTrue(evaluation.remainingCourses().isEmpty());
    }

    @Test
    void evaluateComputerScience2024TracksCompletedAndRemainingAcademicFoundationCourses() {
        Student student = Student.create(
                "24010003",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2024,
                "ACTIVE",
                false
        );

        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2024", "1학기", "C001", "미적분학1", "학문기초", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2024", "2학기", "C002", "고급프로그래밍활용", "학문기초", "3", "GRADE", "A0", "4.0")
        );

        AcademicFoundationCoursePolicyService.AcademicFoundationEvaluation evaluation =
                service.evaluate(student, completedCourses);

        assertTrue(evaluation.policyApplied());
        assertEquals("6", evaluation.earnedCredits().stripTrailingZeros().toPlainString());
        assertEquals(9, evaluation.requiredCredits());
        assertEquals(2, evaluation.completedCourses().size());
        assertEquals(1, evaluation.remainingCourses().size());
        assertEquals("인공지능과빅데이터", evaluation.remainingCourses().getFirst().courseName());
    }
}
