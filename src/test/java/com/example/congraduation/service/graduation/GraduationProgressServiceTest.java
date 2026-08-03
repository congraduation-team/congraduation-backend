package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.congraduation.dto.graduation.CategoryProgressDto;
import com.example.congraduation.dto.graduation.CreditProgressDto;
import com.example.congraduation.dto.graduation.GraduationWorkProgressDto;
import com.example.congraduation.dto.graduation.MajorCreditSummaryDto;
import com.example.congraduation.dto.graduation.MajorTrackProgressDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
