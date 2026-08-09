package com.example.congraduation.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.congraduation.controller.FeedbackController;
import com.example.congraduation.controller.PlannableCourseCatalogController;
import com.example.congraduation.config.WebConfig;
import com.example.congraduation.controller.AdminFeedbackController;
import com.example.congraduation.controller.AuthController;
import com.example.congraduation.controller.StudentController;
import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.feedback.FeedbackStatus;
import com.example.congraduation.domain.feedback.FeedbackType;
import com.example.congraduation.dto.feedback.FeedbackResponseDto;
import com.example.congraduation.dto.plan.PlannableCourseCatalogResponseDto;
import com.example.congraduation.exception.GlobalExceptionHandler;
import com.example.congraduation.service.feedback.FeedbackService;
import com.example.congraduation.service.plan.PlannableCourseCatalogService;
import com.example.congraduation.service.sejong.SejongStudentLoginService;
import com.example.congraduation.service.student.MajorCatalogService;
import com.example.congraduation.service.student.StudentService;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(controllers = {
        StudentController.class,
        AuthController.class,
        AdminFeedbackController.class,
        FeedbackController.class,
        PlannableCourseCatalogController.class
})
@Import({
        WebConfig.class,
        AuthenticatedStudentResolver.class,
        JwtAuthenticationInterceptor.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationWebMvcTest.JwtTestConfig.class
})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "app.jwt.secret=test-jwt-secret-key-with-at-least-32-chars",
        "app.jwt.expiration-seconds=3600"
})
class JwtAuthenticationWebMvcTest {

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtService jwtService() {
            return new JwtService("test-jwt-secret-key-with-at-least-32-chars", 3600);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private StudentService studentService;

    @MockBean
    private MajorCatalogService majorCatalogService;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private PlannableCourseCatalogService plannableCourseCatalogService;

    @MockBean
    private SejongStudentLoginService sejongStudentLoginService;

    private Student student;
    private Student otherStudent;
    private Student adminStudent;

    @BeforeEach
    void setUp() {
        student = student(1L, "21012345", false);
        otherStudent = student(2L, "21012346", false);
        adminStudent = student(3L, "21099999", true);

        when(studentService.getStudent(1L)).thenReturn(student);
        when(studentService.getStudent(2L)).thenReturn(otherStudent);
        when(studentService.getStudent(3L)).thenReturn(adminStudent);
        when(feedbackService.listAllForAdmin()).thenReturn(List.of());
        when(feedbackService.listMine(1L)).thenReturn(List.of(
                new FeedbackResponseDto(
                        10L,
                        FeedbackType.BUG,
                        "문의 제목",
                        "문의 내용",
                        FeedbackStatus.OPEN,
                        1L,
                        "21012345",
                        "테스트",
                        "컴퓨터공학과",
                        "2026-08-08T12:00:00",
                        "2026-08-08T12:00:00",
                        null
                )
        ));
        when(feedbackService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(new FeedbackResponseDto(
                        11L,
                        FeedbackType.BUG,
                        "문의 제목",
                        "문의 내용",
                        FeedbackStatus.OPEN,
                        1L,
                        "21012345",
                        "테스트",
                        "컴퓨터공학과",
                        "2026-08-08T12:00:00",
                        "2026-08-08T12:00:00",
                        null
                ));
        when(plannableCourseCatalogService.getCatalog(null, null, null, null, null, null, null))
                .thenReturn(new PlannableCourseCatalogResponseDto(0, List.of()));
        when(plannableCourseCatalogService.getCatalog(1L, null, null, null, null, null, null))
                .thenReturn(new PlannableCourseCatalogResponseDto(0, List.of()));
    }

    @Test
    void rejectsProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/students/1/major-tracks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void allowsStudentToAccessOwnResourceWithToken() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/students/1/major-tracks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1L));
    }

    @Test
    void rejectsStudentAccessToOtherStudentResource() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/students/2/major-tracks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsCurrentUserLookupWithToken() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.studentNo").value("21012345"));
    }

    @Test
    void rejectsAdminEndpointForNonAdminUser() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/admin/feedbacks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsAdminEndpointForAdminUser() throws Exception {
        String token = jwtService.issueToken(adminStudent).accessToken();

        mockMvc.perform(get("/api/admin/feedbacks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void allowsMineFeedbackWithToken() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/feedbacks/mine")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(1L));
    }

    @Test
    void allowsFeedbackCreateWithMatchingToken() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(post("/api/feedbacks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "type": "BUG",
                                  "title": "문의 제목",
                                  "content": "문의 내용",
                                  "studentId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1L));
    }

    @Test
    void allowsPublicCatalogWithoutToken() throws Exception {
        mockMvc.perform(get("/api/planned-courses/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void rejectsPersonalizedCatalogWithoutToken() throws Exception {
        mockMvc.perform(get("/api/planned-courses/catalog")
                        .param("studentId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsPersonalizedCatalogForOtherStudent() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/planned-courses/catalog")
                        .param("studentId", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsPersonalizedCatalogForOwnStudent() throws Exception {
        String token = jwtService.issueToken(student).accessToken();

        mockMvc.perform(get("/api/planned-courses/catalog")
                        .param("studentId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    private static Student student(Long id, String studentNo, boolean admin) {
        Student student = Student.create(
                studentNo,
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                4,
                2021,
                "ACTIVE",
                admin
        );
        try {
            Field field = Student.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("student id 설정에 실패했습니다.", e);
        }
        return student;
    }
}
