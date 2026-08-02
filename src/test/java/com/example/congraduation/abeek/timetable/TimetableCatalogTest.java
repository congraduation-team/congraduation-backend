package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void latestTermForSemesterReturnsNewestMatchingTerm() throws Exception {
        Path overrideDir = Files.createTempDirectory("timetable-catalog-test");
        writeTerm(overrideDir, new TimetableTermData(2025, 2, List.of()));
        writeTerm(overrideDir, new TimetableTermData(2026, 1, List.of()));
        writeTerm(overrideDir, new TimetableTermData(2026, 2, List.of()));

        TimetableCatalog catalog = new TimetableCatalog(objectMapper, overrideDir.toString());
        catalog.load();

        TimetableTermData latestFall = catalog.latestTermForSemester(2).orElseThrow();

        assertThat(latestFall.termYear()).isEqualTo(2026);
        assertThat(latestFall.semester()).isEqualTo(2);
    }

    private void writeTerm(Path dir, TimetableTermData data) throws Exception {
        Path file = dir.resolve(data.termYear() + "-" + data.semester() + ".json");
        objectMapper.writeValue(file.toFile(), data);
    }
}
