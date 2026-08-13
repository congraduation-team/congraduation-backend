package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.EnglishCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnglishCertificationServiceTest {

    private final EnglishCertificationService service = new EnglishCertificationService();

    @Test
    void evaluateMarksCertifiedWhenSejongCertificationWasApproved() {
        Student student = Student.create(
                "21012345",
                "홍길동",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                4,
                2021,
                "ACTIVE",
                false
        );
        student.updateEnglishCertificationInfo(true, true, "인증완료", "TOEIC", "850", "2026-07-31");

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.satisfied());
        assertEquals("CERTIFIED", result.status());
        assertTrue(result.detail().contains("세종 영어인증 사이트에서 인증 완료로 확인되었습니다."));
    }

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
    void exemptsStudentsAfterTenStandingRegularSemesters() {
        Student student = Student.create("17000001", "테스트", "경영학부", MajorType.SINGLE, null, 5, 2017, "ACTIVE", false);
        List<CompletedCourseUploadRowDto> courses = regularTermsFrom(2017, 10);

        EnglishCertificationProgressDto result = service.evaluate(student, courses);

        assertTrue(result.satisfied());
        assertEquals("EXEMPTED", result.status());
        assertTrue(result.detail().contains("10학기 이수"));
    }

    @Test
    void officialCompletedSemestersPreventsFalseExemptionFromInflatedTranscript() {
        // 기이수는 10학기로 보이지만 classic 이수 학기는 9 → 면제되면 안 됨
        Student student = Student.create(
                "17000002", "테스트", "경영학부", MajorType.SINGLE, null, 5, 9, 2017, "ACTIVE", false
        );
        List<CompletedCourseUploadRowDto> courses = regularTermsFrom(2017, 10);

        EnglishCertificationProgressDto result = service.evaluate(student, courses);

        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
        assertTrue(result.detail().contains("9학기"));
    }

    @Test
    void usesSejongCompletedSemesterCountBeforeTranscriptStanding() {
        Student student = Student.create(
                "21011620", "송대현", "컴퓨터공학과", MajorType.SINGLE, null, 4, 10, 2021, "ACTIVE", false
        );
        List<CompletedCourseUploadRowDto> courses = List.of(
                row("2021", "1학기", "A"),
                row("2021", "2학기", "B"),
                row("2024", "1학기", "C")
        );

        EnglishCertificationProgressDto result = service.evaluate(student, courses);

        assertTrue(result.satisfied());
        assertEquals("EXEMPTED", result.status());
        assertTrue(result.detail().contains("10학기 이수"));
    }

    @Test
    void doesNotExemptWhenCalendarLooksLongButStandingTermsAreFew() {
        // 2021입학 · 2026-1만 있으면 달력으로는 11학기지만, 실제 정규학기는 7개(휴학 건너뜀)
        Student student = Student.create("21011620", "송대현", "컴퓨터공학과", MajorType.SINGLE, null, 4, 2021, "ACTIVE", false);
        List<CompletedCourseUploadRowDto> courses = List.of(
                row("2021", "1학기", "A"),
                row("2021", "2학기", "B"),
                row("2024", "1학기", "C"),
                row("2024", "2학기", "D"),
                row("2025", "1학기", "E"),
                row("2025", "2학기", "F"),
                row("2026", "1학기", "G")
        );

        EnglishCertificationProgressDto result = service.evaluate(student, courses);

        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
        assertTrue(result.detail().contains("7학기"));
        assertFalse(result.detail().contains("11학기"));
    }

    @Test
    void staysInProgressWithoutDetectableExemptionOrSubstitute() {
        Student student = Student.create("24000001", "테스트", "경영학부", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
        assertTrue(result.applicable());
        assertTrue(result.primaryRequirement().contains("TOEIC 800"));
        assertTrue(result.detail().contains("2023학년도 이후 기준 점수"));
    }

    @Test
    void showsLegacyRequirementForPre2023Students() {
        Student student = Student.create("22000003", "테스트", "경영학부", MajorType.SINGLE, null, 4, 2022, "ACTIVE", false);

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.primaryRequirement().contains("TOEIC 700점 이상"));
        assertTrue(result.primaryRequirement().contains("OPIc Intermediate Low 이상"));
        assertTrue(result.primaryRequirement().contains("G-TELP Level 2(65점)"));
        assertTrue(result.detail().contains("2012~2022학년도 기준 점수는 TOEIC 700점 이상"));
    }

    @Test
    void showsEnglishMajorRequirementForEnglishDataConvergenceStudents() {
        Student student = Student.create("24000002", "테스트", "영어데이터융합전공", MajorType.SINGLE, null, 3, 2024, "ACTIVE", false);

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.primaryRequirement().contains("TOEIC 900점 이상"));
        assertTrue(result.primaryRequirement().contains("OPIc Intermediate Mid 2 이상"));
        assertTrue(result.primaryRequirement().contains("G-TELP Speaking Level 3"));
        assertTrue(result.detail().contains("영어영문 계열 2023학년도 이후 기준 점수는 TOEIC 900점 이상"));
    }

    @Test
    void showsLegacyEnglishMajorRequirementFor2021Students() {
        Student student = Student.create("21000001", "테스트", "영어영문학과", MajorType.SINGLE, null, 4, 2021, "ACTIVE", false);

        EnglishCertificationProgressDto result = service.evaluate(student, List.of());

        assertTrue(result.primaryRequirement().contains("TOEIC 800점 이상"));
        assertTrue(result.primaryRequirement().contains("OPIc Intermediate Mid 1 이상"));
        assertTrue(result.primaryRequirement().contains("G-TELP Level 2(77점)"));
        assertTrue(result.detail().contains("영어영문 계열 2012~2022학년도 기준 점수는 TOEIC 800점 이상"));
    }

    private static List<CompletedCourseUploadRowDto> regularTermsFrom(int startYear, int count) {
        List<CompletedCourseUploadRowDto> courses = new ArrayList<>();
        int year = startYear;
        int semester = 1;
        for (int i = 0; i < count; i++) {
            courses.add(row(String.valueOf(year), semester + "학기", "C" + i));
            if (semester == 1) {
                semester = 2;
            } else {
                semester = 1;
                year++;
            }
        }
        return courses;
    }

    private static CompletedCourseUploadRowDto row(String year, String semester, String code) {
        return new CompletedCourseUploadRowDto(
                year, semester, code, "과목" + code, "전선", "3", "GRADE", "B0", "3.0"
        );
    }
}
