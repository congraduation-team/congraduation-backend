package com.example.congraduation.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-jwt-secret-key-with-at-least-32-chars",
            3600,
            new InMemoryJwtRevocationStore()
    );

    @Test
    void issueAndParseToken() {
        Student student = Student.create(
                "21012345",
                "홍길동",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                4,
                2021,
                "ACTIVE",
                true
        );
        setStudentId(student, 1L);

        JwtService.JwtTokenDto tokenDto = jwtService.issueToken(student);
        AuthenticatedStudent authenticatedStudent = jwtService.parseToken(tokenDto.accessToken());

        assertEquals(student.getId(), authenticatedStudent.studentId());
        assertEquals(student.getStudentNo(), authenticatedStudent.studentNo());
        assertTrue(authenticatedStudent.admin());
        assertEquals("Bearer", tokenDto.tokenType());
    }

    @Test
    void rejectsTamperedToken() {
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
        setStudentId(student, 1L);

        JwtService.JwtTokenDto tokenDto = jwtService.issueToken(student);
        String tamperedToken = tokenDto.accessToken().substring(0, tokenDto.accessToken().length() - 1) + "x";

        assertThrows(JwtAuthenticationException.class, () -> jwtService.parseToken(tamperedToken));
    }

    @Test
    void rejectsRevokedToken() {
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
        setStudentId(student, 1L);

        JwtService.JwtTokenDto tokenDto = jwtService.issueToken(student);
        jwtService.revokeToken(tokenDto.accessToken());

        JwtAuthenticationException exception = assertThrows(
                JwtAuthenticationException.class,
                () -> jwtService.parseToken(tokenDto.accessToken())
        );
        assertEquals("로그아웃된 JWT 토큰입니다.", exception.getMessage());
    }

    @Test
    void revokeDoesNotAffectOtherTokensForSameStudent() {
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
        setStudentId(student, 1L);

        String firstToken = jwtService.issueToken(student).accessToken();
        String secondToken = jwtService.issueToken(student).accessToken();
        jwtService.revokeToken(firstToken);

        AuthenticatedStudent authenticatedStudent = jwtService.parseToken(secondToken);
        assertEquals(1L, authenticatedStudent.studentId());
        assertThrows(JwtAuthenticationException.class, () -> jwtService.parseToken(firstToken));
    }

    private static void setStudentId(Student student, Long id) {
        try {
            Field field = Student.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("student id 설정에 실패했습니다.", e);
        }
    }
}
