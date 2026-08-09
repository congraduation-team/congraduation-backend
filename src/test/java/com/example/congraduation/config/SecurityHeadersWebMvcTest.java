package com.example.congraduation.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.congraduation.auth.AuthenticatedStudentResolver;
import com.example.congraduation.auth.JwtAuthenticationInterceptor;
import com.example.congraduation.controller.StudentController;
import com.example.congraduation.dto.student.MajorOptionDto;
import com.example.congraduation.service.student.MajorCatalogService;
import com.example.congraduation.service.student.StudentService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StudentController.class)
@Import({
        WebConfig.class,
        SecurityHeadersFilter.class
})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000,http://127.0.0.1:3000"
})
class SecurityHeadersWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;

    @MockBean
    private MajorCatalogService majorCatalogService;

    @MockBean
    private JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @BeforeEach
    void setUp() {
        when(majorCatalogService.getMajorOptions()).thenReturn(List.of(new MajorOptionDto("컴퓨터공학과")));
    }

    @Test
    void addsBaseSecurityHeadersOnHttpResponse() throws Exception {
        mockMvc.perform(get("/api/students/major-options"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void addsHstsWhenForwardedProtoIsHttps() throws Exception {
        mockMvc.perform(get("/api/students/major-options")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"));
    }
}
