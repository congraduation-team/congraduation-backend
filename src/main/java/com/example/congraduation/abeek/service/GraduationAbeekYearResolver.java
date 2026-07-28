package com.example.congraduation.abeek.service;

import org.springframework.stereotype.Component;

/**
 * 졸업 시점에서 적용할 ABEEK 연도 계산.
 * <p>
 * 예: 2027년 2월 졸업 → 막학기는 2026년 2학기 → ABEEK 2026년도.
 */
@Component
public class GraduationAbeekYearResolver {

    /**
     * @param graduationYear  졸업 연도 (예: 2027)
     * @param graduationMonth 졸업 월 (2 = 전기, 8 = 후기 등)
     * @return 적용 ABEEK 연도
     */
    public int resolve(int graduationYear, int graduationMonth) {
        if (graduationMonth <= 2) {
            // 2월 졸업 → 직전 연도 2학기가 막학기
            return graduationYear - 1;
        }
        // 8월 등 후기 졸업 → 해당 연도 1학기가 막학기
        return graduationYear;
    }
}
