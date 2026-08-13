package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.StudentMajorTrack;
import com.example.congraduation.dto.graduation.DoubleMajorGraduationRequirementProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoubleMajorGraduationRequirementServiceTest {

    private final DoubleMajorGraduationRequirementService service = new DoubleMajorGraduationRequirementService();

    @Test
    void marksDesignInnovationAsGuidanceWithoutGraduationWork() {
        Student student = Student.create("24000001", "테스트", "컴퓨터공학과", MajorType.DOUBLE, null, 3, 2024, "ACTIVE", false);
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.DOUBLE_MAJOR, "디자인이노베이션전공", null, false);

        DoubleMajorGraduationRequirementProgressDto result = service.evaluate(student, track, List.of());

        assertFalse(result.required());
        assertTrue(result.satisfied());
        assertEquals("GUIDANCE", result.status());
        assertTrue(result.detail().contains("주임교수 상담"));
    }

    @Test
    void marksArtsDoubleMajorAsCompletedWhenGraduationWorkCourseExists() {
        Student student = Student.create("24000001", "테스트", "컴퓨터공학과", MajorType.DOUBLE, null, 3, 2024, "ACTIVE", false);
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.DOUBLE_MAJOR, "영화예술학과", null, false);
        CompletedCourseUploadRowDto course = new CompletedCourseUploadRowDto(
                "2026",
                "2학기",
                "005764",
                "졸업작품(P/NP)",
                "복필",
                "0",
                "P/NP",
                "P",
                "0"
        );

        DoubleMajorGraduationRequirementProgressDto result = service.evaluate(student, track, List.of(course));

        assertTrue(result.required());
        assertTrue(result.satisfied());
        assertEquals("COMPLETED", result.status());
    }

    @Test
    void guidesArtsToArtsSubstituteWhenGraduationWorkMissing() {
        Student student = Student.create("24000001", "테스트", "음악과", MajorType.DOUBLE, null, 3, 2024, "ACTIVE", false);
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.DOUBLE_MAJOR, "영화예술학과", null, false);

        DoubleMajorGraduationRequirementProgressDto result = service.evaluate(student, track, List.of());

        assertTrue(result.required());
        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
        assertTrue(result.detail().contains("지정 전공선택 3학점"));
        assertTrue(result.detail().contains("학과 상담"));
    }

    @Test
    void guidesMangaSubstituteWhenGraduationWorkMissing() {
        Student student = Student.create("24000001", "테스트", "컴퓨터공학과", MajorType.DOUBLE, null, 3, 2024, "ACTIVE", false);
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.DOUBLE_MAJOR, "만화애니메이션텍전공", null, false);

        DoubleMajorGraduationRequirementProgressDto result = service.evaluate(student, track, List.of());

        assertTrue(result.required());
        assertFalse(result.satisfied());
        assertEquals("IN_PROGRESS", result.status());
        assertTrue(result.detail().contains("지정 전공선택 3학점"));
    }

    @Test
    void guidesIoTExchangeMicrodegreeSeparatelyFromSharedCourses() {
        Student student = Student.create("24000001", "테스트", "컴퓨터공학과", MajorType.DOUBLE, null, 3, 2024, "ACTIVE", false);
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.DOUBLE_MAJOR, "지능IoT학과", null, false);

        DoubleMajorGraduationRequirementProgressDto result = service.evaluate(student, track, List.of());

        assertTrue(result.required());
        assertTrue(result.satisfied());
        assertEquals("GUIDANCE", result.status());
        assertTrue(result.detail().contains("교류형"));
        assertTrue(result.detail().contains("공유형"));
        assertTrue(result.detail().contains("9~12"));
    }
}
