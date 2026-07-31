package com.example.congraduation.roadmap.service;

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
        ));

        assertThat(mapper.resolveTermKey("2021", "1학기")).isEqualTo("1-1");
        assertThat(mapper.resolveTermKey("2021", "2학기")).isEqualTo("1-2");
        assertThat(mapper.resolveTermKey("2024", "1학기")).isEqualTo("2-1");
        assertThat(mapper.resolveTermKey("2023", "1학기")).isNull();
    }

    @Test
    void seasonalAttachesToPreviousRegularTerm() {
        TranscriptStandingMapper mapper = TranscriptStandingMapper.fromRows(List.of(
                row("2021", "1학기", "A"),
                row("2021", "여름학기", "B"),
                row("2021", "2학기", "C")
        ));

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
