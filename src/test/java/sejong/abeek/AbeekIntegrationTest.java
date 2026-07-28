package sejong.abeek;

import com.example.congraduation.CongraduationApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CongraduationApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AbeekIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("유효 요건 API: 2021 입학 + 2026 졸업ABEEK → 설계 10")
    void effectiveRequirement() throws Exception {
        mockMvc.perform(get("/api/curriculum/effective-requirement")
                        .param("entranceYear", "2021")
                        .param("graduationAbeekYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designMinCredits").value(10))
                .andExpect(jsonPath("$.majorMinCredits").value(45));
    }

    @Test
    @DisplayName("2026 신설 SW-AI종합설계는 2021 입학자에게 면제")
    void swAiWaivedFor2021Entrant() throws Exception {
        String body = """
                {
                  "studentId": "21012345",
                  "name": "홍길동",
                  "entranceYear": 2021,
                  "graduationAbeekYear": 2026,
                  "enrollments": [
                    {"courseCode":"MAJ_BASIC_DESIGN","credits":3,"designCredits":3,"takenYear":2022,"takenSemester":1},
                    {"courseCode":"MAJ_CPP","credits":3,"designCredits":1,"takenYear":2022,"takenSemester":2},
                    {"courseCode":"MAJ_CAPSTONE","credits":6,"designCredits":6,"takenYear":2025,"takenSemester":1}
                  ]
                }
                """;

        mockMvc.perform(post("/api/abeek/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/abeek/students/21012345/abeek-evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.design.requiredCredits").value(10))
                .andExpect(jsonPath("$.design.earnedCredits").value(10))
                .andExpect(jsonPath("$.waivedGraduationOnlyCourses[*].courseCode", hasItem("MAJ_SW_AI")))
                .andExpect(jsonPath("$.waivedGraduationOnlyCourses[?(@.courseCode=='MAJ_SW_AI')].waived", hasItem(true)));
    }

    @Test
    @DisplayName("커리큘럼 연도 조회")
    void curriculumYear() throws Exception {
        mockMvc.perform(get("/api/curriculum/2026/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.courseCode=='MAJ_SW_AI')].newlyIntroducedRequired", hasItem(true)));
    }
}
