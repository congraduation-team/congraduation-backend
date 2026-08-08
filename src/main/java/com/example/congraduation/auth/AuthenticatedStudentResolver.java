package com.example.congraduation.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedStudentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public AuthenticatedStudentResolver(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public AuthenticatedStudent require(HttpServletRequest request) {
        Object authenticatedStudent = request.getAttribute(AuthRequestAttributes.AUTHENTICATED_STUDENT);
        if (authenticatedStudent instanceof AuthenticatedStudent student) {
            return student;
        }
        throw new JwtAuthenticationException("인증된 사용자 정보가 없습니다.");
    }

    public AuthenticatedStudent optional(HttpServletRequest request) {
        Object authenticatedStudent = request.getAttribute(AuthRequestAttributes.AUTHENTICATED_STUDENT);
        if (authenticatedStudent instanceof AuthenticatedStudent student) {
            return student;
        }

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtAuthenticationException("Authorization Bearer 토큰 형식이 올바르지 않습니다.");
        }
        return jwtService.parseToken(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
    }

    public Long resolveStudentIdForOptionalScopedRequest(HttpServletRequest request, Long requestedStudentId) {
        AuthenticatedStudent authenticatedStudent = optional(request);
        if (requestedStudentId == null) {
            return authenticatedStudent == null ? null : authenticatedStudent.studentId();
        }
        if (authenticatedStudent == null) {
            throw new JwtAuthenticationException("개인화된 조회에는 Authorization Bearer 토큰이 필요합니다.");
        }
        if (!requestedStudentId.equals(authenticatedStudent.studentId())) {
            throw new JwtAuthorizationException("다른 학생의 데이터에 접근할 수 없습니다.");
        }
        return authenticatedStudent.studentId();
    }
}
