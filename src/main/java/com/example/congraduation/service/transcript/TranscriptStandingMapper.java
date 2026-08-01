package com.example.congraduation.service.transcript;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 기이수 정규학기 순번 → 로드맵 termKey (1-1~4-2).
 * <p>
 * 입학년도 이후 정규학기만 센다. 달력 연도(takenYear − admissionYear + 1)로 학년을 만들지 않는다.
 * 예: 2021입학, 기이수 2021-1 / 2021-2 / 2024-1 → 1-1 / 1-2 / 2-1
 * 입학 전 수강은 1-1에 붙이고, 계절학기는 직전 정규학기에 귀속.
 */
public final class TranscriptStandingMapper {

    public static final List<String> TERM_KEYS = List.of(
            "1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2"
    );

    private final Map<RegularTerm, String> regularTermKeys;
    private final List<RegularTerm> orderedRegularTerms;
    private final Integer admissionYear;

    private TranscriptStandingMapper(
            Map<RegularTerm, String> regularTermKeys,
            List<RegularTerm> orderedRegularTerms,
            Integer admissionYear
    ) {
        this.regularTermKeys = regularTermKeys;
        this.orderedRegularTerms = orderedRegularTerms;
        this.admissionYear = admissionYear;
    }

    public static TranscriptStandingMapper fromRows(List<CompletedCourseUploadRowDto> rows, Integer admissionYear) {
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
            events.add(new TermEvent(year, kind));
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
            // 입학 전 학기는 순번에서 제외
            if (admissionYear != null && event.year() < admissionYear) {
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
        return new TranscriptStandingMapper(Map.copyOf(regularKeys), List.copyOf(ordered), admissionYear);
    }

    /**
     * 입학 이후 실제로 이수한 정규학기(1·2학기) 개수.
     * 휴학으로 비는 연도는 세지 않으며, 로드맵 8학기 상한과 무관하다.
     * 달력식 (takenYear−admissionYear)×2+학기 금지.
     */
    public static int countDistinctRegularTerms(List<CompletedCourseUploadRowDto> rows, Integer admissionYear) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        LinkedHashSet<RegularTerm> terms = new LinkedHashSet<>();
        for (CompletedCourseUploadRowDto row : rows) {
            Integer year = parseYear(row.year());
            if (year == null) {
                continue;
            }
            if (admissionYear != null && year < admissionYear) {
                continue;
            }
            SemesterKind kind = classifySemester(row.semester());
            if (!kind.regular()) {
                continue;
            }
            terms.add(new RegularTerm(year, kind.regularSemester()));
        }
        return terms.size();
    }

    /**
     * 대응 로드맵 칸이 있을 때만 termKey 반환. 임의 fallback 없음.
     */
    public String resolveTermKey(String yearText, String semesterText) {
        Integer year = parseYear(yearText);
        SemesterKind kind = classifySemester(semesterText);
        if (year == null || kind == SemesterKind.UNKNOWN) {
            return null;
        }

        // 입학 전 수강 → 1-1 (순번에 넣지 않음)
        if (admissionYear != null && year < admissionYear) {
            return orderedRegularTerms.isEmpty() ? null : TERM_KEYS.get(0);
        }

        if (kind.regular()) {
            return regularTermKeys.get(new RegularTerm(year, kind.regularSemester()));
        }

        // 계절학기 → 직전 정규학기 (입학 이후만)
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
        if (previous != null) {
            return regularTermKeys.get(previous);
        }
        return orderedRegularTerms.isEmpty() ? null : TERM_KEYS.get(0);
    }

    /** 1~8. 매핑 없으면 0. */
    public int resolveStep(String yearText, String semesterText) {
        String key = resolveTermKey(yearText, semesterText);
        if (key == null) {
            return 0;
        }
        int index = TERM_KEYS.indexOf(key);
        return index < 0 ? 0 : index + 1;
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

    private record TermEvent(int year, SemesterKind kind) {
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
