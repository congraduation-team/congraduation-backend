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
        if (allowsAnonymous(request)) {
            return true;
        }

        AuthenticatedStudent authenticatedStudent = authenticate(request);
        authorize(request, authenticatedStudent);
        request.setAttribute(AuthRequestAttributes.AUTHENTICATED_STUDENT, authenticatedStudent);
        return true;
    }

    /**
     * 학과 공용 로드맵처럼 개인 이수 정보가 붙지 않는 조회는 비로그인 허용.
     */
    private boolean allowsAnonymous(HttpServletRequest request) {
        String path = normalizePath(request);
        if ("/api/roadmap".equals(path) && isBlank(request.getParameter("studentDbId"))) {
            return true;
        }
        if ("/api/abeek/full-roadmap".equals(path) && isBlank(request.getParameter("studentId"))) {
            return true;
        }
        return false;
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
        String requestUri = normalizePath(request);
        if (requestUri.startsWith("/api/admin/") || "/api/admin".equals(requestUri)) {
            if (!authenticatedStudent.admin()) {
                throw new JwtAuthorizationException("관리자 권한이 없습니다.");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        );

        validateStudentDbId(request.getParameter("studentDbId"), authenticatedStudent);
        validateStudentNo(request.getParameter("studentNo"), authenticatedStudent);

        if (pathVariables != null) {
            validateStudentDbId(pathVariables.get("studentDbId"), authenticatedStudent);

            String pathStudentId = pathVariables.get("studentId");
            if (requestUri.startsWith("/api/abeek/students")) {
                // ABEEK path {studentId} = 학번(studentNo)
                validateStudentNo(pathStudentId, authenticatedStudent);
            } else {
                // /api/students/{studentId}, transcripts 등 = DB PK
                validateStudentDbId(pathStudentId, authenticatedStudent);
            }
        }

        // ABEEK query studentId 는 학번
        if (requestUri.startsWith("/api/abeek/") && !isBlank(request.getParameter("studentId"))) {
            validateStudentNo(request.getParameter("studentId"), authenticatedStudent);
        }
    }

    private void validateStudentDbId(String rawStudentDbId, AuthenticatedStudent authenticatedStudent) {
        if (isBlank(rawStudentDbId)) {
            return;
        }
        long requestedStudentId;
        try {
            requestedStudentId = Long.parseLong(rawStudentDbId.trim());
        } catch (NumberFormatException e) {
            throw new JwtAuthorizationException("학생 식별자가 올바르지 않습니다.");
        }
        if (requestedStudentId != authenticatedStudent.studentId()) {
            throw new JwtAuthorizationException("다른 학생의 데이터에 접근할 수 없습니다.");
        }
    }

    private void validateStudentNo(String rawStudentNo, AuthenticatedStudent authenticatedStudent) {
        if (isBlank(rawStudentNo)) {
            return;
        }
        String requested = rawStudentNo.trim();
        String authenticatedNo = authenticatedStudent.studentNo() == null
                ? ""
                : authenticatedStudent.studentNo().trim();
        if (!requested.equals(authenticatedNo)) {
            throw new JwtAuthorizationException("다른 학생의 데이터에 접근할 수 없습니다.");
        }
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path == null ? "" : path;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
