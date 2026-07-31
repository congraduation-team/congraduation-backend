package com.example.congraduation.roadmap.service;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기이수 정규학기 순번 → 로드맵 termKey (1-1~4-2).
 * <p>
 * 예: 2021입학 후 2021-1, 2021-2, (휴학), 2024-1 → 1-1, 1-2, 2-1
 * 계절학기는 직전 정규학기에 귀속.
 */
final class TranscriptStandingMapper {

    private static final List<String> TERM_KEYS = List.of(
            "1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2"
    );

    private final Map<RegularTerm, String> regularTermKeys;
    private final List<RegularTerm> orderedRegularTerms;

    private TranscriptStandingMapper(
            Map<RegularTerm, String> regularTermKeys,
            List<RegularTerm> orderedRegularTerms
    ) {
        this.regularTermKeys = regularTermKeys;
        this.orderedRegularTerms = orderedRegularTerms;
    }

    static TranscriptStandingMapper fromRows(List<CompletedCourseUploadRowDto> rows) {
        List<TermEvent> events = new ArrayList<>();
        for (CompletedCourseUploadRowDto row : rows) {
            Integer year = parseYear(row.year());
            if (year == null) {
                continue;
            }
            SemesterKind kind = classifySemester(row.semester());
            if (kind == SemesterKind.UNKNOWN) {
                continue;
            }
            events.add(new TermEvent(year, kind, row.semester()));
        }

        events.sort(Comparator
                .comparingInt(TermEvent::year)
                .thenComparingInt(e -> e.kind().sortOrder()));

        LinkedHashMap<RegularTerm, String> regularKeys = new LinkedHashMap<>();
        List<RegularTerm> ordered = new ArrayList<>();
        for (TermEvent event : events) {
            if (!event.kind().regular()) {
                continue;
            }
            RegularTerm term = new RegularTerm(event.year(), event.kind().regularSemester());
            if (regularKeys.containsKey(term)) {
                continue;
            }
            if (ordered.size() >= TERM_KEYS.size()) {
                continue;
            }
            String key = TERM_KEYS.get(ordered.size());
            ordered.add(term);
            regularKeys.put(term, key);
        }
        return new TranscriptStandingMapper(Map.copyOf(regularKeys), List.copyOf(ordered));
    }

    /**
     * 실제 수강 학기가 정규/계절로 해석되고, 대응 로드맵 칸이 있을 때만 termKey 반환.
     * 없으면 null (임의 fallback 금지).
     */
    String resolveTermKey(String yearText, String semesterText) {
        Integer year = parseYear(yearText);
        SemesterKind kind = classifySemester(semesterText);
        if (year == null || kind == SemesterKind.UNKNOWN) {
            return null;
        }

        if (kind.regular()) {
            return regularTermKeys.get(new RegularTerm(year, kind.regularSemester()));
        }

        // 계절학기 → 직전 정규학기
        RegularTerm previous = null;
        int seasonalOrder = kind.sortOrder();
        for (RegularTerm term : orderedRegularTerms) {
            int termOrder = term.semester() == 1 ? 10 : 20;
            if (term.year() < year || (term.year() == year && termOrder < seasonalOrder)) {
                previous = term;
            } else {
                break;
            }
        }
        return previous == null ? null : regularTermKeys.get(previous);
    }

    private static Integer parseYear(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.trim().replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(digits.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static SemesterKind classifySemester(String semesterText) {
        if (semesterText == null || semesterText.isBlank()) {
            return SemesterKind.UNKNOWN;
        }
        String normalized = semesterText.trim();
        if (normalized.contains("여름")) {
            return SemesterKind.SUMMER;
        }
        if (normalized.contains("겨울")) {
            return SemesterKind.WINTER;
        }
        if (normalized.contains("2")) {
            return SemesterKind.FALL;
        }
        if (normalized.contains("1")) {
            return SemesterKind.SPRING;
        }
        return SemesterKind.UNKNOWN;
    }

    private record TermEvent(int year, SemesterKind kind, String rawSemester) {
    }

    private record RegularTerm(int year, int semester) {
    }

    enum SemesterKind {
        SPRING(10, true, 1),
        SUMMER(15, false, 0),
        FALL(20, true, 2),
        WINTER(25, false, 0),
        UNKNOWN(99, false, 0);

        private final int sortOrder;
        private final boolean regular;
        private final int regularSemester;

        SemesterKind(int sortOrder, boolean regular, int regularSemester) {
            this.sortOrder = sortOrder;
            this.regular = regular;
            this.regularSemester = regularSemester;
        }

        int sortOrder() {
            return sortOrder;
        }

        boolean regular() {
            return regular;
        }

        int regularSemester() {
            return regularSemester;
        }
    }
}
