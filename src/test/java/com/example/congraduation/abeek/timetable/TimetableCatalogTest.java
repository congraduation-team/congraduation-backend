package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void latestTermForSemesterReturnsNewestMatchingTerm() throws Exception {
        Path overrideDir = Files.createTempDirectory("timetable-catalog-test");
        writeTerm(overrideDir, new TimetableTermData(2025, 2, List.of(sampleOffering())));
        writeTerm(overrideDir, new TimetableTermData(2026, 1, List.of(sampleOffering())));
        writeTerm(overrideDir, new TimetableTermData(2026, 2, List.of(sampleOffering())));

        TimetableCatalog catalog = new TimetableCatalog(objectMapper, overrideDir.toString());
        catalog.load();

        TimetableTermData latestFall = catalog.latestTermForSemester(2).orElseThrow();

        assertThat(latestFall.termYear()).isEqualTo(2026);
        assertThat(latestFall.semester()).isEqualTo(2);
    }

    @Test
    void ignoresEmptyAndSparseOverrides() throws Exception {
        Path overrideDir = Files.createTempDirectory("timetable-catalog-sparse");
        // empty override must not wipe classpath 2026-1
        writeTerm(overrideDir, new TimetableTermData(2026, 1, List.of()));

        TimetableCatalog catalog = new TimetableCatalog(objectMapper, overrideDir.toString());
        catalog.load();

        TimetableTermData spring = catalog.latestTermForSemester(1).orElseThrow();
        assertThat(spring.termYear()).isEqualTo(2026);
        assertThat(spring.offerings()).isNotEmpty();
    }

    @Test
    void alignsTermIdentityFromFileNameWhenJsonSemesterMismatches() throws Exception {
        Path probeDir = Files.createTempDirectory("timetable-catalog-align-probe");
        TimetableCatalog probe = new TimetableCatalog(objectMapper, probeDir.toString());
        probe.load();
        TimetableTermData classpathFall = probe.latestTermForSemester(2).orElseThrow();

        Path overrideDir = Files.createTempDirectory("timetable-catalog-align");
        List<TimetableOffering> offerings = new ArrayList<>(classpathFall.offerings());
        offerings.add(new TimetableOffering(
                "공대", "컴퓨터공학과", "FALLONLY1", "001", "가을전용마커",
                "전공선택", "4", 3.0, "이론", null, null, null, null
        ));
        // 파일명은 2026-2 인데 JSON semester=1 → 가을 슬롯으로 보정되어야 함
        objectMapper.writeValue(
                overrideDir.resolve("2026-2.json").toFile(),
                new TimetableTermData(2026, 1, offerings)
        );

        TimetableCatalog catalog = new TimetableCatalog(objectMapper, overrideDir.toString());
        catalog.load();

        TimetableTermData fall = catalog.findTerm(2026, 2).orElseThrow();
        assertThat(fall.semester()).isEqualTo(2);
        assertThat(fall.offerings()).extracting(TimetableOffering::courseCode).contains("FALLONLY1");

        TimetableTermData spring = catalog.findTerm(2026, 1).orElseThrow();
        assertThat(spring.offerings()).extracting(TimetableOffering::courseCode).doesNotContain("FALLONLY1");
        assertThat(spring.offerings()).isNotEmpty();
    }

    @Test
    void skipsSpringOverrideThatDuplicatesFallOfferings() throws Exception {
        Path probeDir = Files.createTempDirectory("timetable-catalog-probe");
        TimetableCatalog probe = new TimetableCatalog(objectMapper, probeDir.toString());
        probe.load();
        TimetableTermData classpathFall = probe.latestTermForSemester(2).orElseThrow();
        assertThat(classpathFall.termYear()).isEqualTo(2026);

        Path overrideDir = Files.createTempDirectory("timetable-catalog-dup-spring");
        // 가을 시간표를 2026-1로 올려도 classpath 봄을 덮지 않아야 함
        writeTerm(overrideDir, new TimetableTermData(2026, 1, classpathFall.offerings()));

        TimetableCatalog catalog = new TimetableCatalog(objectMapper, overrideDir.toString());
        catalog.load();

        TimetableTermData spring = catalog.findTerm(2026, 1).orElseThrow();
        TimetableTermData fall = catalog.findTerm(2026, 2).orElseThrow();
        assertThat(TimetableCatalog.isNearDuplicateTerm(spring, fall)).isFalse();
        assertThat(spring.offerings())
                .extracting(TimetableOffering::courseCode)
                .contains("006132", "010111", "011924");
    }

    @Test
    void alignTermIdentityAndNearDuplicateHelpers() {
        TimetableTermData aligned = TimetableCatalog.alignTermIdentity(
                "2026-2.json",
                new TimetableTermData(2026, 1, List.of(sampleOffering()))
        );
        assertThat(aligned.termYear()).isEqualTo(2026);
        assertThat(aligned.semester()).isEqualTo(2);

        TimetableTermData same = new TimetableTermData(2026, 1, List.of(sampleOffering()));
        assertThat(TimetableCatalog.isNearDuplicateTerm(same, same)).isTrue();
        assertThat(TimetableCatalog.isNearDuplicateTerm(
                same,
                new TimetableTermData(2026, 2, List.of(
                        new TimetableOffering(
                                "공대", "컴퓨터공학과", "011514", "001", "컴퓨터비전",
                                "전공선택", "4", 3.0, "이론", null, null, null, null
                        )
                ))
        )).isFalse();
    }

    private static TimetableOffering sampleOffering() {
        return new TimetableOffering(
                "공대", "컴퓨터공학과", "009960", "001", "Capstone",
                "전공필수", "4", 6.0, "이론", null, null, null, null
        );
    }

    private void writeTerm(Path dir, TimetableTermData data) throws Exception {
        Path file = dir.resolve(data.termYear() + "-" + data.semester() + ".json");
        objectMapper.writeValue(file.toFile(), data);
    }
}
