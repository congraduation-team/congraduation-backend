package com.example.congraduation.abeek.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.congraduation.abeek.domain.CourseMaster;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.StudentEnrollment;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;
import com.example.congraduation.abeek.domain.enums.ElectiveArea;
import com.example.congraduation.abeek.dto.DesignEvaluationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignCreditEvaluatorTest {

    private DesignCreditEvaluator evaluator;
    private Map<String, CurriculumCourse> curriculum;

    @BeforeEach
    void setUp() {
        evaluator = new DesignCreditEvaluator();
        curriculum = new HashMap<>();
        curriculum.put("MAJ_BASIC_DESIGN", course("MAJ_BASIC_DESIGN", "공학설계기초(산학프로젝트입문)", DesignLevel.BASIC, 3));
        curriculum.put("MAJ_CPP", course("MAJ_CPP", "문제해결및실습:C++", DesignLevel.ELEMENT, 1));
        curriculum.put("MAJ_DB", course("MAJ_DB", "데이터베이스", DesignLevel.ELEMENT, 1));
        curriculum.put("MAJ_CAPSTONE", course("MAJ_CAPSTONE", "Capstone디자인(산학협력프로젝트)", DesignLevel.COMPREHENSIVE, 6));
        curriculum.put("MAJ_ADV_C", course("MAJ_ADV_C", "고급C프로그래밍및실습", DesignLevel.ELEMENT, 1));
    }

    @Test
    @DisplayName("기초→요소→종합 정상 순서면 설계학점 전부 인정")
    void normalSequence() {
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_BASIC_DESIGN", 3, 2022, 1),
                enrollment("MAJ_CPP", 1, 2022, 2),
                enrollment("MAJ_DB", 1, 2023, 1),
                enrollment("MAJ_CAPSTONE", 6, 2024, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(11);
        assertThat(result.isSequenceSatisfied()).isTrue();
        assertThat(result.isHasBasicDesign()).isTrue();
        assertThat(result.isHasElementDesign()).isTrue();
        assertThat(result.isHasComprehensiveDesign()).isTrue();
    }

    @Test
    @DisplayName("요소설계를 기초설계보다 먼저 들으면 해당 요소 설계학점 불인정")
    void elementBeforeBasicNotCounted() {
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_CPP", 1, 2022, 1),
                enrollment("MAJ_BASIC_DESIGN", 3, 2022, 2),
                enrollment("MAJ_DB", 1, 2023, 1),
                enrollment("MAJ_CAPSTONE", 6, 2024, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(10); // 3+1+6, CPP(1) 제외
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_CPP") && !c.isRecognized());
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_DB") && c.isRecognized());
    }

    @Test
    @DisplayName("기초설계와 요소설계 병수는 인정")
    void concurrentBasicAndElementAllowed() {
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_BASIC_DESIGN", 3, 2022, 1),
                enrollment("MAJ_CPP", 1, 2022, 1),
                enrollment("MAJ_CAPSTONE", 6, 2024, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(10);
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_CPP") && c.isRecognized()
                        && c.getReason().contains("병수"));
    }

    @Test
    @DisplayName("종합설계 이후 수강한 요소설계는 불인정")
    void elementAfterComprehensiveNotCounted() {
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_BASIC_DESIGN", 3, 2022, 1),
                enrollment("MAJ_CPP", 1, 2022, 2),
                enrollment("MAJ_CAPSTONE", 6, 2024, 1),
                enrollment("MAJ_DB", 1, 2024, 2)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(10); // DB 제외
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_DB") && !c.isRecognized());
    }

    @Test
    @DisplayName("요소설계와 종합설계 병수는 인정")
    void concurrentElementAndComprehensiveAllowed() {
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_BASIC_DESIGN", 3, 2022, 1),
                enrollment("MAJ_CPP", 1, 2022, 2),
                enrollment("MAJ_DB", 1, 2024, 1),
                enrollment("MAJ_CAPSTONE", 6, 2024, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(11);
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_DB") && c.isRecognized());
    }

    @Test
    @DisplayName("소수 설계학점도 누적해 인정한다")
    void decimalDesignCreditsAreRecognized() {
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_BASIC_DESIGN", 3.0, 2022, 1),
                enrollment("MAJ_CPP", 1.5, 2022, 2),
                enrollment("MAJ_CAPSTONE", 6.0, 2024, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(10.5);
    }

    @Test
    @DisplayName("기초 전에 들은 고급C·디지털은 제외, 기초와 병수한 DB만 요소로 인정 → 4학점")
    void elementBeforeBasicExcluded_concurrentDbCounts() {
        curriculum.put("MAJ_DIGITAL", course("MAJ_DIGITAL", "디지털시스템", DesignLevel.ELEMENT, 1));
        List<StudentEnrollment> enrollments = List.of(
                enrollment("MAJ_ADV_C", 1, 2021, 1),
                enrollment("MAJ_DIGITAL", 1, 2021, 1),
                enrollment("MAJ_BASIC_DESIGN", 3, 2022, 1),
                enrollment("MAJ_DB", 1, 2022, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getRecognizedDesignCredits()).isEqualTo(4);
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_ADV_C") && !c.isRecognized());
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_DIGITAL") && !c.isRecognized());
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("MAJ_DB") && c.isRecognized());
        assertThat(result.isHasBasicDesign()).isTrue();
        assertThat(result.isHasElementDesign()).isTrue();
        assertThat(result.isHasComprehensiveDesign()).isFalse();
    }

    @Test
    @DisplayName("커리큘럼이 기초/종합을 ELEMENT로 오표기해도 과목명으로 단계 보정")
    void wrongElementLabelStillAppliesSequence() {
        curriculum.clear();
        curriculum.put("SW_BASIC", course("SW_BASIC", "SW설계기초(산학프로젝트입문)", DesignLevel.ELEMENT, 3));
        curriculum.put("SW_EL", course("SW_EL", "데이터베이스", DesignLevel.ELEMENT, 1));
        curriculum.put("SW_EL2", course("SW_EL2", "오픈소스SW공학", DesignLevel.ELEMENT, 1));
        curriculum.put("SW_CAP", course("SW_CAP", "Capstone디자인(산학협력프로젝트)", DesignLevel.ELEMENT, 6));

        List<StudentEnrollment> enrollments = List.of(
                enrollment("SW_EL", 1, 2021, 1),
                enrollment("SW_BASIC", 3, 2022, 1),
                enrollment("SW_EL2", 1, 2022, 2),
                enrollment("SW_CAP", 6, 2024, 1)
        );

        DesignEvaluationResult result = evaluator.evaluate(enrollments, curriculum);

        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("SW_BASIC") && c.getDesignLevel() == DesignLevel.BASIC);
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("SW_CAP") && c.getDesignLevel() == DesignLevel.COMPREHENSIVE);
        assertThat(result.getCourses()).anyMatch(c ->
                c.getCourseCode().equals("SW_EL") && !c.isRecognized());
        assertThat(result.getRecognizedDesignCredits()).isEqualTo(10); // 3+1+6
        assertThat(result.isSequenceSatisfied()).isTrue();
    }

    private CurriculumCourse course(String code, String name, DesignLevel level, double design) {
        CourseMaster master = CourseMaster.builder()
                .courseCode(code)
                .name(name)
                .category(CourseCategory.MAJOR)
                .equivalenceGroup(code)
                .electiveArea(ElectiveArea.NONE)
                .departmentCourse(true)
                .build();
        return CurriculumCourse.builder()
                .curriculumYear(2021)
                .courseMaster(master)
                .credits(3)
                .designCredits(design)
                .designLevel(level)
                .role(CourseRole.REQUIRED)
                .build();
    }

    private StudentEnrollment enrollment(String code, double design, int year, int semester) {
        CurriculumCourse cc = curriculum.get(code);
        return StudentEnrollment.builder()
                .courseMaster(cc.getCourseMaster())
                .credits(cc.getCredits())
                .designCredits(design)
                .takenYear(year)
                .takenSemester(semester)
                .passed(true)
                .build();
    }
}
