package com.example.congraduation.abeek.timetable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfDirectedCreativeOfferingsTest {

    @Test
    void ensureIncludedAppendsAllSeedCoursesWhenMissing() {
        List<TimetableOffering> merged = SelfDirectedCreativeOfferings.ensureIncluded(List.of());

        assertThat(merged).hasSize(10);
        assertThat(merged)
                .extracting(TimetableOffering::courseName)
                .containsExactly(
                        "자기주도창의교양Ⅰ",
                        "자기주도창의교양Ⅱ",
                        "자기주도창의교양Ⅲ",
                        "자기주도창의교양Ⅳ",
                        "자기주도창의교양Ⅴ",
                        "자기주도창의전공Ⅰ",
                        "자기주도창의전공Ⅱ",
                        "자기주도창의전공Ⅲ",
                        "자기주도창의전공Ⅳ",
                        "자기주도창의전공"
                );
        assertThat(merged)
                .extracting(TimetableOffering::courseCode)
                .containsExactly(
                        "000001", "000002", "000003", "000004", "000005",
                        "000006", "000007", "000008", "000009", "000010"
                );
        assertThat(merged)
                .filteredOn(o -> "자기주도창의전공".equals(o.courseName()))
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.credits()).isEqualTo(6.0);
                    assertThat(o.category()).isEqualTo("전공");
                    assertThat(o.courseCode()).isEqualTo("000010");
                });
        assertThat(merged)
                .filteredOn(o -> o.courseName().startsWith("자기주도창의교양"))
                .allSatisfy(o -> assertThat(o.category()).isEqualTo("교양선택"));
        assertThat(merged)
                .filteredOn(o -> o.courseName().matches("자기주도창의전공[ⅠⅡⅢⅣ]"))
                .allSatisfy(o -> assertThat(o.category()).isEqualTo("전공선택"));
    }

    @Test
    void ensureIncludedDoesNotDuplicateExistingCodesOrNames() {
        List<TimetableOffering> existing = new ArrayList<>();
        existing.add(new TimetableOffering(
                "대양휴머니티칼리지", "대양휴머니티칼리지", "010418", "002",
                "자기주도창의전공Ⅰ", "전공선택", "1", 3.0,
                "이론", "", "", "", "대양휴머니티칼리지"
        ));
        existing.add(new TimetableOffering(
                "대양휴머니티칼리지", "대양휴머니티칼리지", "OTHER", "001",
                "자기주도창의교양Ⅲ", "교양선택", "1", 3.0,
                "이론", "", "", "", "대양휴머니티칼리지"
        ));

        List<TimetableOffering> merged = SelfDirectedCreativeOfferings.ensureIncluded(existing);

        assertThat(merged).hasSize(10); // 2 existing + 8 missing seeds
        assertThat(merged.stream().filter(o -> "자기주도창의전공Ⅰ".equals(o.courseName())).count()).isEqualTo(1);
        assertThat(merged.stream().filter(o -> "자기주도창의교양Ⅲ".equals(o.courseName())).count()).isEqualTo(1);
        assertThat(merged).extracting(TimetableOffering::courseCode)
                .contains("000010")
                .doesNotContain("000003", "000006"); // 이름 중복으로 스킵
    }
}
