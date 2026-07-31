package com.example.congraduation.service.transcript;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptStandingMapperTest {

    @Test
    void mapsRegularTermsByChronologicalOrder_skippingGapYears() {
        TranscriptStandingMapper mapper = TranscriptStandingMapper.fromRows(List.of(
                row("2021", "1학기", "A"),
                row("2021", "2학기", "B"),
                row("2024", "1학기", "C")
        ), 2021);

        assertThat(mapper.resolveTermKey("2021", "1학기")).isEqualTo("1-1");
        assertThat(mapper.resolveTermKey("2021", "2학기")).isEqualTo("1-2");
        assertThat(mapper.resolveTermKey("2024", "1학기")).isEqualTo("2-1");
        assertThat(mapper.resolveTermKey("2023", "1학기")).isNull();
        assertThat(mapper.resolveStep("2024", "1학기")).isEqualTo(3);
    }

    @Test
    void dataStructureInThirdRegularTerm_isStanding2_1() {
        // 2021입학: 1-1, 1-2 이수 후 휴학, 2024-1에 자료구조 → 2-1 (달력 4-1 아님)
        TranscriptStandingMapper mapper = TranscriptStandingMapper.fromRows(List.of(
                row("2020", "1학기", "PRE"),
                row("2021", "1학기", "C"),
                row("2021", "2학기", "ADV_C"),
                row("2024", "1학기", "009952")
        ), 2021);

        assertThat(mapper.resolveTermKey("2024", "1학기")).isEqualTo("2-1");
        assertThat(mapper.resolveTermKey("2020", "1학기")).isEqualTo("1-1");
    }

    @Test
    void doesNotUseCalendarRelativeGrade() {
        // takenYear - admissionYear + 1 이면 2026 → 6학년. 기이수 순번이면 4-1.
        TranscriptStandingMapper mapper = TranscriptStandingMapper.fromRows(List.of(
                row("2021", "1학기", "A"),
                row("2021", "2학기", "B"),
                row("2024", "1학기", "C"),
                row("2024", "2학기", "D"),
                row("2025", "1학기", "E"),
                row("2025", "2학기", "F"),
                row("2026", "1학기", "G")
        ), 2021);

        assertThat(mapper.resolveTermKey("2026", "1학기")).isEqualTo("4-1");
        assertThat(mapper.resolveStep("2026", "1학기")).isEqualTo(7);
    }

    @Test
    void seasonalAttachesToPreviousRegularTerm() {
        TranscriptStandingMapper mapper = TranscriptStandingMapper.fromRows(List.of(
                row("2021", "1학기", "A"),
                row("2021", "여름학기", "B"),
                row("2021", "2학기", "C")
        ), 2021);

        assertThat(mapper.resolveTermKey("2021", "1학기")).isEqualTo("1-1");
        assertThat(mapper.resolveTermKey("2021", "여름학기")).isEqualTo("1-1");
        assertThat(mapper.resolveTermKey("2021", "2학기")).isEqualTo("1-2");
    }

    private static CompletedCourseUploadRowDto row(String year, String semester, String code) {
        return new CompletedCourseUploadRowDto(
                year, semester, code, "과목" + code, "전선", "3", "GRADE", "A", "4.0", null
        );
    }
}
