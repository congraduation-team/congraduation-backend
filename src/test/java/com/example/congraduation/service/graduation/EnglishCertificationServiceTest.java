package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.EnglishCertificationProgressDto;
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
}
