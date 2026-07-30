package com.example.congraduation.abeek.service;

import org.springframework.stereotype.Component;

/**
 * 졸업 예정 연도(표시)와 공학인증(ABEEK) 적용 연도를 분리해 계산한다.
 * <p>
 * 규칙(기이수 마지막 수강 학기 기준):
 * <ul>
 *   <li>공학인증 연도 = 마지막 수강 연도 (1학기/2학기 모두 그 해 ABEEK)</li>
 *   <li>마지막이 Y년 1학기이고 이수 위치가 4-2이면 → Y년 졸업 예정 (후기 등), ABEEK도 Y</li>
 *   <li>마지막이 Y년 1학기이고 이수 위치가 4-1(또는 그 이전)이면 → Y+1년 졸업 예정, ABEEK는 Y</li>
 *   <li>마지막이 Y년 2학기이면 → Y+1년 졸업 예정(전기), ABEEK는 Y</li>
 * </ul>
 * 예: 2026-1에 Capstone(4-1) 이수 → 표시 "2027년 졸업 예정 기준", 요건은 ABEEK 2026.
 */
@Component
public class GraduationAbeekYearResolver {

    public record GraduationTiming(
            int expectedGraduationYear,
            int abeekYear,
            int lastTakenYear,
            int lastTakenSemester,
            String standingTerm
    ) {}

    /**
     * @param graduationYear  졸업 연도 (예: 2027)
     * @param graduationMonth 졸업 월 (2 = 전기, 8 = 후기 등)
     * @return 적용 ABEEK 연도
     */
    public int resolve(int graduationYear, int graduationMonth) {
        if (graduationMonth <= 2) {
            return graduationYear - 1;
        }
        return graduationYear;
    }

    /**
     * 기이수 마지막 학기 + 해당 학기 이수 위치(권장학년학기)로 졸업예정/ABEEK 연도를 계산한다.
     *
     * @param lastTakenYear            마지막 수강 연도
     * @param lastTakenSemester        1 또는 2
     * @param standingTermInLastTerm   마지막 학기에 이수한 과목 중 가장 늦은 권장학기 (예: "4-1"). 없으면 null
     */
    public GraduationTiming resolveFromLastTerm(
            int lastTakenYear,
            int lastTakenSemester,
            String standingTermInLastTerm
    ) {
        int abeekYear = lastTakenYear;
        int semester = lastTakenSemester <= 1 ? 1 : 2;
        String standing = normalizeTerm(standingTermInLastTerm);

        int expectedGraduationYear;
        if (semester == 1 && isAtLeast(standing, 4, 2)) {
            // 1학기에 4-2 과정 → 그 해 졸업 예정, ABEEK도 그 해
            expectedGraduationYear = lastTakenYear;
        } else {
            // 1학기 4-1 등 / 2학기 막학기 → 다음 해 졸업 예정, ABEEK는 마지막 수강 연도
            expectedGraduationYear = lastTakenYear + 1;
        }

        return new GraduationTiming(
                expectedGraduationYear,
                abeekYear,
                lastTakenYear,
                semester,
                standing == null || standing.isBlank() ? null : standing
        );
    }

    public String basisLabel(int expectedGraduationYear) {
        return expectedGraduationYear + "년 졸업 예정 기준";
    }

    private static String normalizeTerm(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return term.replaceAll("\\s+", "");
    }

    /** "4-1" 형태가 grade/semester 이상인지 */
    private static boolean isAtLeast(String term, int grade, int semester) {
        if (term == null) {
            return false;
        }
        String[] parts = term.split("-");
        if (parts.length != 2) {
            return false;
        }
        try {
            int g = Integer.parseInt(parts[0].replaceAll("\\D", ""));
            int s = Integer.parseInt(parts[1].replaceAll("\\D", ""));
            if (g > grade) {
                return true;
            }
            return g == grade && s >= semester;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
