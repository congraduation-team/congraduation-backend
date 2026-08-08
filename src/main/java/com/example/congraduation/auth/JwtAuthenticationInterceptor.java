package com.example.congraduation.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthenticatedStudent authenticatedStudent = authenticate(request);
        authorize(request, authenticatedStudent);
        request.setAttribute(AuthRequestAttributes.AUTHENTICATED_STUDENT, authenticatedStudent);
        return true;
    }

    private AuthenticatedStudent authenticate(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtAuthenticationException("Authorization Bearer 토큰이 필요합니다.");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new JwtAuthenticationException("Authorization Bearer 토큰이 비어 있습니다.");
        }

        return jwtService.parseToken(token);
    }

    private void authorize(HttpServletRequest request, AuthenticatedStudent authenticatedStudent) {
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/admin/") || "/api/admin".equals(requestUri)) {
            if (!authenticatedStudent.admin()) {
                throw new JwtAuthorizationException("관리자 권한이 없습니다.");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        );
        if (pathVariables == null) {
            return;
        }

        validateStudentPathVariable(pathVariables.get("studentId"), authenticatedStudent);
        validateStudentPathVariable(pathVariables.get("studentDbId"), authenticatedStudent);
    }

    private void validateStudentPathVariable(String pathStudentId, AuthenticatedStudent authenticatedStudent) {
        if (pathStudentId == null || pathStudentId.isBlank()) {
            return;
        }
        long requestedStudentId;
        try {
            requestedStudentId = Long.parseLong(pathStudentId);
        } catch (NumberFormatException e) {
            throw new JwtAuthorizationException("학생 식별자가 올바르지 않습니다.");
        }
        if (requestedStudentId != authenticatedStudent.studentId()) {
            throw new JwtAuthorizationException("다른 학생의 데이터에 접근할 수 없습니다.");
        }
    }
}
