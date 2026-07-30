package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.StudentMajorTrack;
import com.example.congraduation.dto.graduation.MajorTrackProgressDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinorTrackProgressServiceTest {

    private final MinorTrackProgressService service = new MinorTrackProgressService();

    @Test
    void completesMinorWhenCreditsReachRequirement() {
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.MINOR, "경영학부", null, false);
        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("부필", "9", null, false, null, List.of()),
                new CategorySummaryDto("부선", "12", null, false, null, List.of())
        );

        MajorTrackProgressDto result = service.evaluate(track, summaries);

        assertEquals("COMPLETED", result.status());
        assertEquals("21", result.totalCredits().requiredCredits());
        assertNull(result.requiredCourseProgress());
    }

    @Test
    void returnsManualCheckForIotMinorAfterCreditCompletion() {
        StudentMajorTrack track = StudentMajorTrack.create(MajorType.MINOR, "지능IoT학과", null, false);
        List<CategorySummaryDto> summaries = List.of(
                new CategorySummaryDto("부선", "21", null, false, null, List.of())
        );

        MajorTrackProgressDto result = service.evaluate(track, summaries);

        assertEquals("MANUAL_CHECK_REQUIRED", result.status());
    }
}
