package com.example.congraduation.service.transcript;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.TranscriptSummaryDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptSummaryCalculatorTest {

    private final TranscriptSummaryCalculator calculator = new TranscriptSummaryCalculator();

    @Test
    void summarizeExcludesFailedCoursesFromEarnedCreditsButKeepsFInGpa() {
        List<CompletedCourseUploadRowDto> courses = List.of(
                new CompletedCourseUploadRowDto("2026", "1학기", "A001", "전공과목", "전필", "3", "GRADE", "A+", "4.5"),
                new CompletedCourseUploadRowDto("2026", "1학기", "A002", "재수강실패", "전필", "3", "GRADE", "F", "0"),
                new CompletedCourseUploadRowDto("2026", "1학기", "A003", "패논패실패", "교선", "2", "P/NP", "NP", "0"),
                new CompletedCourseUploadRowDto("2026", "1학기", "A004", "패논패통과", "교선", "1", "P/NP", "P", "0")
        );

        TranscriptSummaryDto summary = calculator.summarize(courses);

        assertEquals("4", summary.totalCredits());
        assertEquals("13.5", summary.totalGradePoints());
        assertEquals("2.25", summary.averageGradePoint());
        assertEquals(2, summary.categorySummaries().size());
        assertEquals("3", summary.categorySummaries().get(0).earnedCredits());
        assertEquals("1", summary.categorySummaries().get(1).earnedCredits());
    }
}
