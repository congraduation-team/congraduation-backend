package com.example.congraduation.config;

import com.example.congraduation.auth.JwtAuthenticationInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    public WebConfig(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            JwtAuthenticationInterceptor jwtAuthenticationInterceptor
    ) {
        this.allowedOrigins = sanitizeOrigins(allowedOrigins);
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
    }

    private static List<String> sanitizeOrigins(List<String> origins) {
        List<String> sanitized = origins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (sanitized.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("app.cors.allowed-origins에는 와일드카드(*)를 쓸 수 없습니다. 프론트 주소를 정확히 적어 주세요.");
        }
        return sanitized;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 기본 차단: /api/** 전체에 JWT 인증·본인 확인을 걸고, 개인 데이터가 없는 공개 API만 예외로 둔다.
        // 새 API를 추가하면 자동으로 보호되며, 공개가 필요할 때만 PUBLIC_API_PATHS에 명시한다.
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_API_PATHS);
    }

    /**
     * 토큰 없이 호출 가능한 API. 경로 단위로 제외하므로 같은 경로에 개인 데이터를 다루는 메서드를 추가하지 말 것.
     * 조건부 공개(/api/roadmap, /api/abeek/full-roadmap 의 학생 파라미터 없는 조회)는
     * JwtAuthenticationInterceptor#allowsAnonymous 에서 처리한다.
     */
    static final String[] PUBLIC_API_PATHS = {
            // 인증 자체
            "/api/auth/login",
            "/api/auth/logout",                 // 컨트롤러가 Bearer 토큰을 직접 검증·폐기
            // 학과·교육과정·시간표 등 공용 기준 데이터
            "/api/departments",
            "/api/students/major-options",
            "/api/curriculum/**",
            "/api/abeek/timetable-terms",
            "/api/abeek/offered-courses",
            "/api/roadmap/departments",
            // studentId를 주면 컨트롤러가 토큰·본인 여부를 검사(AuthenticatedStudentResolver)
            "/api/planned-courses/catalog",
            // 사이트 통계(집계 수치만)
            "/api/stats/summary",
            "/api/stats/visit"
    };
}
