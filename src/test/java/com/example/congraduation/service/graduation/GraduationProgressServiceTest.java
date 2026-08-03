package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.CategoryProgressDto;
import com.example.congraduation.dto.graduation.CreditProgressDto;
import com.example.congraduation.dto.graduation.GraduationWorkProgressDto;
import com.example.congraduation.dto.graduation.MajorCreditSummaryDto;
import com.example.congraduation.dto.graduation.MajorTrackProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraduationProgressServiceTest {

    @Test
    void buildCategoryProgressSumsAliasCategoriesTogether() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "buildCategoryProgress",
                List.class,
                Integer.class,
                String[].class
        );
        method.setAccessible(true);

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("공필", "8", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("교필", "5", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전기", "9", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전공기초", "6", null, false, null, new ArrayList<>())
        );

        CategoryProgressDto commonLiberal = (CategoryProgressDto) method.invoke(
                service,
                summaries,
                13,
                new String[]{"공필", "교필"}
        );
        CategoryProgressDto majorFoundation = (CategoryProgressDto) method.invoke(
                service,
                summaries,
                15,
                new String[]{"전기", "전공기초"}
        );

        assertEquals("13", commonLiberal.earnedCredits());
        assertEquals(true, commonLiberal.satisfied());
        assertEquals("15", majorFoundation.earnedCredits());
        assertEquals(true, majorFoundation.satisfied());
    }

    @Test
    void calculateMajorFoundationCreditsCountsItFoundationCoursesEvenWhenTheyAreMajorRequired() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null,
                null,
                null,
                null,
                new DepartmentCurriculumPolicyService(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "calculateMajorFoundationCredits",
                Student.class,
                DepartmentCurriculumPolicy.class,
                List.class,
                List.class
        );
        method.setAccessible(true);

        Student student = Student.create(
                "24012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2024,
                "ACTIVE",
                false
        );
        DepartmentCurriculumPolicy policy = new DepartmentCurriculumPolicy(
                "컴퓨터공학과",
                2024,
                13,
                9,
                9,
                15,
                130,
                60,
                21,
                39,
                Map.of("전기", 15, "전필", 21, "전선", 39)
        );

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("전필", "15", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전기", "0", null, false, null, new ArrayList<>())
        );
        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2024", "1학기", "009912", "C프로그래밍및실습", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2024", "2학기", "009955", "고급C프로그래밍및실습", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2024", "1학기", "007330", "확률및통계", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2024", "2학기", "001725", "선형대수", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2024", "2학기", "007313", "공업수학1", "전필", "3", "GRADE", "A0", "4.0")
        );

        BigDecimal earned = (BigDecimal) method.invoke(service, student, policy, summaries, completedCourses);

        assertEquals(0, new BigDecimal("15").compareTo(earned));
    }

    @Test
    void resolveMajorFoundationCourseRuleReturnsItFoundationSetForComputerScience2024() {
        DepartmentCurriculumPolicyService service = new DepartmentCurriculumPolicyService();
        Student student = Student.create(
                "24012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2024,
                "ACTIVE",
                false
        );

        DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule =
                service.resolveMajorFoundationCourseRule(student);

        assertEquals(
                Set.of("확률및통계", "확률통계및프로그래밍", "C프로그래밍및실습", "고급C프로그래밍및실습", "선형대수", "선형대수및프로그래밍", "공업수학1"),
                rule.requiredCourseNames()
        );
        assertEquals(Set.of(), rule.optionalCourseNames());
        assertEquals(0, rule.optionalCourseLimit());
    }

    @Test
    void resolveMajorFoundationCourseRuleReturnsBusinessHotelSetFor2026() {
        DepartmentCurriculumPolicyService service = new DepartmentCurriculumPolicyService();
        Student student = Student.create(
                "26012345",
                "테스트",
                "경영학부",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "ACTIVE",
                false
        );

        DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule =
                service.resolveMajorFoundationCourseRule(student);

        assertEquals(Set.of("경영학원론", "경제학원론", "Hospitality경영원론"), rule.requiredCourseNames());
        assertEquals(Set.of(), rule.optionalCourseNames());
        assertEquals(0, rule.optionalCourseLimit());
    }

    @Test
    void resolveMajorFoundationCourseRuleReturnsNaturalLifeSetFor2026() {
        DepartmentCurriculumPolicyService service = new DepartmentCurriculumPolicyService();
        Student student = Student.create(
                "26022345",
                "테스트",
                "화학과",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "ACTIVE",
                false
        );

        DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule =
                service.resolveMajorFoundationCourseRule(student);

        assertEquals(
                Set.of("일반물리학1", "일반화학1", "일반생물학1"),
                rule.requiredCourseNames()
        );
        assertEquals(
                Set.of("미적분학2", "기초통계학", "기초천문학", "기초생물통계학", "일반물리학2", "일반화학2", "일반생물학2"),
                rule.optionalCourseNames()
        );
        assertEquals(2, rule.optionalCourseLimit());
    }

    @Test
    void resolveMajorFoundationCourseRuleReturnsEmptyFor2023ComputerScience() {
        DepartmentCurriculumPolicyService service = new DepartmentCurriculumPolicyService();
        Student student = Student.create(
                "23012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2023,
                "ACTIVE",
                false
        );

        DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule =
                service.resolveMajorFoundationCourseRule(student);

        assertEquals(Set.of(), rule.requiredCourseNames());
        assertEquals(Set.of(), rule.optionalCourseNames());
        assertEquals(0, rule.optionalCourseLimit());
    }

    @Test
    void resolvePolicyHasNoMajorFoundationRequirementFor2021To2023ComputerScience() {
        DepartmentCurriculumPolicyService service = new DepartmentCurriculumPolicyService();

        Student student2021 = Student.create(
                "21012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2021,
                "ACTIVE",
                false
        );
        Student student2022 = Student.create(
                "22012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2022,
                "ACTIVE",
                false
        );
        Student student2023 = Student.create(
                "23012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2023,
                "ACTIVE",
                false
        );

        assertEquals(null, service.resolve(student2021).majorFoundationCredits());
        assertEquals(null, service.resolve(student2022).majorFoundationCredits());
        assertEquals(null, service.resolve(student2023).majorFoundationCredits());
    }

    @Test
    void normalizeCoursesForPolicyTreatsLegacyMajorFoundationAsMajorElectiveWhenRequirementIsAbsent() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "normalizeCoursesForPolicy",
                List.class,
                DepartmentCurriculumPolicy.class
        );
        method.setAccessible(true);

        DepartmentCurriculumPolicy policy = new DepartmentCurriculumPolicy(
                "컴퓨터공학과",
                2023,
                13,
                6,
                15,
                null,
                130,
                72,
                33,
                39,
                Map.of("기필", 15, "전필", 33, "전선", 39)
        );

        List<CompletedCourseUploadRowDto> courses = List.of(
                new CompletedCourseUploadRowDto("2023", "2학기", "007313", "공업수학1", "전기", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2023", "2학기", "009954", "알고리즘및실습", "전필", "3", "GRADE", "A0", "4.0")
        );

        @SuppressWarnings("unchecked")
        List<CompletedCourseUploadRowDto> normalized =
                (List<CompletedCourseUploadRowDto>) method.invoke(service, courses, policy);

        assertEquals("전선", normalized.getFirst().category());
        assertEquals("전필", normalized.get(1).category());
    }

    @Test
    void calculateCommonLiberalCreditsCountsEquivalentCourseRegardlessOfCategory() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null,
                null,
                null,
                null,
                null,
                null,
                new BalancedLiberalCoursePolicyService(),
                null,
                null,
                null,
                null,
                null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "calculateCommonLiberalCredits",
                Student.class,
                List.class,
                List.class
        );
        method.setAccessible(true);

        Student student = Student.create(
                "24012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                2,
                2024,
                "ACTIVE",
                false
        );

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("공필", "11", null, false, null, new ArrayList<>())
        );
        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2024", "2학기", "GEN001", "취창업과진로설계", "교선", "1", "GRADE", "A0", "4.0")
        );

        BigDecimal earned = (BigDecimal) method.invoke(service, student, summaries, completedCourses);

        assertEquals(0, new BigDecimal("1").compareTo(earned));
    }

    @Test
    void calculateCommonLiberalCreditsCounts2026ReplacementCourse() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null,
                null,
                null,
                null,
                null,
                null,
                new BalancedLiberalCoursePolicyService(),
                null,
                null,
                null,
                null,
                null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "calculateCommonLiberalCredits",
                Student.class,
                List.class,
                List.class
        );
        method.setAccessible(true);

        Student student = Student.create(
                "26012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "ACTIVE",
                false
        );

        List<CategorySummaryDto> summaries = List.of();
        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2026", "2학기", "GEN900", "취업과진로역량개발", "교양", "1", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2026", "2학기", "GEN901", "창업과기업가정신", "교양", "1", "GRADE", "A0", "4.0")
        );

        BigDecimal earned = (BigDecimal) method.invoke(service, student, summaries, completedCourses);

        assertEquals(0, new BigDecimal("2").compareTo(earned));
    }

    @Test
    void evaluateBalancedLiberalTreats2021StudentAsNotApplicable() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null,
                null,
                null,
                null,
                null,
                null,
                new BalancedLiberalCoursePolicyService(),
                null,
                null,
                null,
                null,
                null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "evaluateBalancedLiberal",
                Student.class,
                List.class
        );
        method.setAccessible(true);

        Student student = Student.create(
                "21012345",
                "테스트",
                "컴퓨터공학과",
                MajorType.SINGLE,
                null,
                3,
                2021,
                "ACTIVE",
                false
        );

        Object evaluation = method.invoke(service, student, List.of());
        Method progressMethod = evaluation.getClass().getDeclaredMethod("progress");
        progressMethod.setAccessible(true);
        CategoryProgressDto progress = (CategoryProgressDto) progressMethod.invoke(evaluation);

        assertTrue(progress.satisfied());
        assertEquals(null, progress.requiredCredits());
    }

    @Test
    void calculateMajorFoundationCreditsCountsOnlyTwoNaturalLifeOptionalCourses() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null,
                null,
                null,
                null,
                new DepartmentCurriculumPolicyService(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "calculateMajorFoundationCredits",
                Student.class,
                DepartmentCurriculumPolicy.class,
                List.class,
                List.class
        );
        method.setAccessible(true);

        Student student = Student.create(
                "26022345",
                "테스트",
                "화학과",
                MajorType.SINGLE,
                null,
                1,
                2026,
                "ACTIVE",
                false
        );
        DepartmentCurriculumPolicy policy = new DepartmentCurriculumPolicy(
                "화학과",
                2026,
                12,
                9,
                9,
                16,
                130,
                60,
                15,
                45,
                Map.of("전기", 16, "전필", 15, "전선", 45)
        );

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("전필", "0", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전기", "0", null, false, null, new ArrayList<>())
        );
        List<CompletedCourseUploadRowDto> completedCourses = List.of(
                new CompletedCourseUploadRowDto("2026", "1학기", "PHY1", "일반물리학1", "전필", "4", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2026", "1학기", "CHEM1", "일반화학1", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2026", "1학기", "BIO1", "일반생물학1", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2026", "2학기", "PHY2", "일반물리학2", "전필", "4", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2026", "2학기", "CALC2", "미적분학2", "전필", "3", "GRADE", "A0", "4.0"),
                new CompletedCourseUploadRowDto("2026", "2학기", "STATB", "기초통계학", "전필", "3", "GRADE", "A0", "4.0")
        );

        BigDecimal earned = (BigDecimal) method.invoke(service, student, policy, summaries, completedCourses);

        assertEquals(0, new BigDecimal("17").compareTo(earned));
    }

    @Test
    void buildMajorCreditSummaryExcludesMajorFoundationFromMajorTotal() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Method method = java.util.Arrays.stream(GraduationProgressService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("buildMajorCreditSummary"))
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("전필", "21", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전선", "39", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전기", "15", null, false, null, new ArrayList<>())
        );

        com.example.congraduation.dto.transcript.TranscriptSummaryDto transcriptSummary =
                new com.example.congraduation.dto.transcript.TranscriptSummaryDto(
                        "120",
                        "420",
                        "3.5",
                        summaries
                );
        DepartmentCurriculumPolicy policy = new DepartmentCurriculumPolicy(
                "컴퓨터공학과",
                2024,
                13,
                9,
                9,
                15,
                130,
                60,
                21,
                39,
                Map.of("전기", 15, "전필", 21, "전선", 39)
        );

        MajorCreditSummaryDto result = (MajorCreditSummaryDto) method.invoke(
                service,
                transcriptSummary,
                policy,
                null,
                new BigDecimal("15")
        );

        assertEquals("60", result.earnedMajorCredits());
        assertEquals("15", result.earnedMajorFoundationCredits());
    }

    @Test
    void buildCategoryProgressTreatsZeroRequirementAsSatisfied() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "buildCategoryProgress",
                List.class,
                Integer.class,
                String[].class
        );
        method.setAccessible(true);

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("교선", "0", null, false, null, new ArrayList<>())
        );

        CategoryProgressDto electiveLiberal = (CategoryProgressDto) method.invoke(
                service,
                summaries,
                0,
                new String[]{"교선"}
        );

        assertEquals(true, electiveLiberal.satisfied());
    }

    @Test
    void applyCategoryRequirementsMergesAliasCategoriesIntoRequiredBucket() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "applyCategoryRequirements",
                List.class,
                Map.class
        );
        method.setAccessible(true);

        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("전공기초", "6", null, false, null, new ArrayList<>()),
                new CategorySummaryDto("전기", "3", null, false, null, new ArrayList<>())
        );

        @SuppressWarnings("unchecked")
        List<CategorySummaryDto> result = (List<CategorySummaryDto>) method.invoke(
                service,
                summaries,
                Map.of("전기", 9)
        );

        assertEquals(1, result.size());
        assertEquals("전기", result.getFirst().category());
        assertEquals("9", result.getFirst().earnedCredits());
        assertEquals(true, result.getFirst().satisfied());
    }

    @Test
    void buildGraduationBlockersSkipsDedicatedCategoryDuplicates() throws Exception {
        GraduationProgressService service = new GraduationProgressService(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Method method = GraduationProgressService.class.getDeclaredMethod(
                "buildGraduationBlockers",
                CreditProgressDto.class,
                CategoryProgressDto.class,
                CategoryProgressDto.class,
                CategoryProgressDto.class,
                CategoryProgressDto.class,
                CategoryProgressDto.class,
                MajorCreditSummaryDto.class,
                List.class,
                GraduationWorkProgressDto.class,
                List.class
        );
        method.setAccessible(true);

        CreditProgressDto satisfiedCredit = new CreditProgressDto("130", "130", true, "100.00");
        CategoryProgressDto satisfiedCategory = new CategoryProgressDto("9", "9", true, "100.00");
        CategoryProgressDto unsatisfiedMajorFoundation = new CategoryProgressDto("6", "9", false, "66.67");
        MajorCreditSummaryDto majorCreditSummary = new MajorCreditSummaryDto(
                "60", "60", true, "100.00",
                "15", "15", true, "100.00",
                "24", "24", true, "100.00",
                "9", 5, 8, 16
        );
        GraduationWorkProgressDto graduationWork = new GraduationWorkProgressDto(
                false, false, "NOT_APPLICABLE", "NOT_REQUIRED", "해당 없음"
        );
        List<CategorySummaryDto> categorySummaries = List.of(
                new CategorySummaryDto("전기", "6", "9", false, "66.67", new ArrayList<>()),
                new CategorySummaryDto("전선", "24", "24", true, "100.00", new ArrayList<>()),
                new CategorySummaryDto("복필", "0", "3", false, "0.00", new ArrayList<>())
        );

        @SuppressWarnings("unchecked")
        List<String> blockers = (List<String>) method.invoke(
                service,
                satisfiedCredit,
                satisfiedCategory,
                satisfiedCategory,
                satisfiedCategory,
                satisfiedCategory,
                unsatisfiedMajorFoundation,
                majorCreditSummary,
                List.<MajorTrackProgressDto>of(),
                graduationWork,
                categorySummaries
        );

        assertEquals(true, blockers.contains("전공기초 요건을 충족하지 못했습니다."));
        assertFalse(blockers.contains("전기 이수구분 요건을 충족하지 못했습니다."));
        assertEquals(true, blockers.contains("복필 이수구분 요건을 충족하지 못했습니다."));
    }
}
