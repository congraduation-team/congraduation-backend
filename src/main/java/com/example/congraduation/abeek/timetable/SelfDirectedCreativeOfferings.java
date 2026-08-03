package com.example.congraduation.abeek.timetable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 세종창의학기제 자기주도창의 과목.
 * 강의시간표 원본에 빠져 있어도 업로드 시 항상 병합한다.
 *
 * <p>학수번호는 예약 대역 {@code 000001}~{@code 000010}을 사용한다.
 * (기존 세종 시간표에 해당 번호 과목 없음 확인)
 */
public final class SelfDirectedCreativeOfferings {

    private static final String COLLEGE = "대양휴머니티칼리지";

    private SelfDirectedCreativeOfferings() {
    }

    /** 업로드/저장 직전에 호출. 동일 학수번호 또는 정규화 과목명이 있으면 추가하지 않는다. */
    public static List<TimetableOffering> ensureIncluded(List<TimetableOffering> offerings) {
        List<TimetableOffering> merged = new ArrayList<>(offerings == null ? List.of() : offerings);
        Map<String, TimetableOffering> byCode = new LinkedHashMap<>();
        Map<String, TimetableOffering> byName = new LinkedHashMap<>();
        for (TimetableOffering offering : merged) {
            if (offering == null) {
                continue;
            }
            String code = normalizeCode(offering.courseCode());
            if (!code.isEmpty()) {
                byCode.putIfAbsent(code, offering);
            }
            String name = normalizeName(offering.courseName());
            if (!name.isEmpty()) {
                byName.putIfAbsent(name, offering);
            }
        }

        for (TimetableOffering seed : seeds()) {
            String code = normalizeCode(seed.courseCode());
            String name = normalizeName(seed.courseName());
            if ((!code.isEmpty() && byCode.containsKey(code))
                    || (!name.isEmpty() && byName.containsKey(name))) {
                continue;
            }
            merged.add(seed);
            if (!code.isEmpty()) {
                byCode.put(code, seed);
            }
            if (!name.isEmpty()) {
                byName.put(name, seed);
            }
        }
        return merged;
    }

    static List<TimetableOffering> seeds() {
        List<TimetableOffering> list = new ArrayList<>();
        // 교양선택 · 자기계발과 진로
        list.add(liberal("000001", "자기주도창의교양Ⅰ", 1.0));
        list.add(liberal("000002", "자기주도창의교양Ⅱ", 2.0));
        list.add(liberal("000003", "자기주도창의교양Ⅲ", 3.0));
        list.add(liberal("000004", "자기주도창의교양Ⅳ", 3.0));
        list.add(liberal("000005", "자기주도창의교양Ⅴ", 3.0));
        // 전공선택
        list.add(majorElective("000006", "자기주도창의전공Ⅰ", 3.0));
        list.add(majorElective("000007", "자기주도창의전공Ⅱ", 3.0));
        list.add(majorElective("000008", "자기주도창의전공Ⅲ", 3.0));
        list.add(majorElective("000009", "자기주도창의전공Ⅳ", 3.0));
        // 자기주도창의전공 6학점 (이수구분: 전공)
        list.add(major("000010", "자기주도창의전공", 6.0));
        return list;
    }

    private static TimetableOffering liberal(String courseCode, String courseName, double credits) {
        return offering(courseCode, courseName, "교양선택", credits);
    }

    private static TimetableOffering majorElective(String courseCode, String courseName, double credits) {
        return offering(courseCode, courseName, "전공선택", credits);
    }

    private static TimetableOffering major(String courseCode, String courseName, double credits) {
        return offering(courseCode, courseName, "전공", credits);
    }

    private static TimetableOffering offering(
            String courseCode,
            String courseName,
            String category,
            double credits
    ) {
        return new TimetableOffering(
                COLLEGE,
                COLLEGE,
                courseCode,
                "001",
                courseName,
                category,
                "1",
                credits,
                "이론",
                "",
                "",
                "",
                COLLEGE
        );
    }

    private static String normalizeCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeName(String courseName) {
        if (courseName == null || courseName.isBlank()) {
            return "";
        }
        // 로마숫자·공백 표기 차이: "자기주도창의교양 Ⅲ" ↔ "자기주도창의교양Ⅲ"
        return courseName.replaceAll("\\s+", "");
    }
}
