package com.example.congraduation.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.congraduation.controller.AuthController;
import com.example.congraduation.service.sejong.SejongStudentLoginService;
import com.example.congraduation.service.student.StudentService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * app.cors.allowed-origins를 덮어쓰지 않아 application.properties의 운영 기본값으로 동작한다.
 */
@WebMvcTest(controllers = AuthController.class)
class LoginRateLimitWebMvcTest {

    private static final String PRODUCTION_FRONTEND = "https://congraduation-frontend.vercel.app";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SejongStudentLoginService sejongStudentLoginService;

    @MockBean
    private StudentService studentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Test
    void returns429WithRetryAfterWhenLoginRateLimited() throws Exception {
        when(sejongStudentLoginService.login(any(), anyString()))
                .thenThrow(new LoginRateLimitExceededException(Duration.ofSeconds(90)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"21012345\",\"password\":\"pw\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "90"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void corsAllowsProductionFrontendByDefault() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", PRODUCTION_FRONTEND)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PRODUCTION_FRONTEND));
    }

    @Test
    void corsRejectsLocalhostByDefault() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
