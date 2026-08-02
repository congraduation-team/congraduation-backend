package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.congraduation.dto.graduation.CategoryProgressDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
}
