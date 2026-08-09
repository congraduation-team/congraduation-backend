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
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173}")
            List<String> allowedOrigins,
            JwtAuthenticationInterceptor jwtAuthenticationInterceptor
    ) {
        this.allowedOrigins = allowedOrigins;
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
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
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns(
                        "/api/auth/me",
                        "/api/feedbacks",
                        "/api/feedbacks/mine",
                        "/api/students/*/major-tracks",
                        "/api/students/*/major-track",
                        "/api/students/*/planned-courses",
                        "/api/students/*/planned-semesters/**",
                        "/api/evaluate/graduation-progress/*",
                        "/api/transcripts/status/*",
                        "/api/transcripts/upload/*",
                        "/api/transcripts/*/major-credits",
                        "/api/admin/**"
                );
    }
}
