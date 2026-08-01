package com.example.congraduation.roadmap.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 로드맵 이수 매칭용 학수번호 동등 그룹.
 * 커리큘럼 개편으로 과목명·학수번호가 바뀐 경우(예: 확률통계및프로그래밍 ↔ 확률및통계)를 동일 이수로 본다.
 */
final class RoadmapCourseCodeEquivalence {

    private static final Map<String, String> TO_CANONICAL;
    private static final Map<String, Set<String>> GROUP_MEMBERS;

    static {
        Map<String, String> toCanonical = new LinkedHashMap<>();
        Map<String, Set<String>> members = new LinkedHashMap<>();

        // 확률통계및프로그래밍(구) ↔ 확률및통계(신)
        link(toCanonical, members, "009959", "007330");
        // 선형대수및프로그래밍(구) ↔ 선형대수(신)
        link(toCanonical, members, "009961", "001725");

        TO_CANONICAL = Collections.unmodifiableMap(toCanonical);
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : members.entrySet()) {
            frozen.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        GROUP_MEMBERS = Collections.unmodifiableMap(frozen);
    }

    private RoadmapCourseCodeEquivalence() {
    }

    static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String trimmed = code.trim();
        String stripped = trimmed.replaceFirst("^0+(?!$)", "");
        return stripped.toUpperCase(Locale.ROOT);
    }

    /** 동등 그룹 대표 키. 그룹 없으면 자기 자신. */
    static String canonical(String courseCode) {
        String norm = normalize(courseCode);
        if (norm.isBlank()) {
            return "";
        }
        return TO_CANONICAL.getOrDefault(norm, norm);
    }

    /** 자기 자신 포함 동등 학수번호 목록(정규화). */
    static List<String> equivalentsIncludingSelf(String courseCode) {
        String norm = normalize(courseCode);
        if (norm.isBlank()) {
            return List.of();
        }
        String canon = canonical(norm);
        Set<String> group = GROUP_MEMBERS.get(canon);
        if (group == null || group.isEmpty()) {
            return List.of(norm);
        }
        return List.copyOf(group);
    }

    private static void link(
            Map<String, String> toCanonical,
            Map<String, Set<String>> members,
            String... codes
    ) {
        List<String> norms = new ArrayList<>();
        for (String code : codes) {
            String n = normalize(code);
            if (!n.isBlank()) {
                norms.add(n);
            }
        }
        if (norms.isEmpty()) {
            return;
        }
        String canon = norms.get(0);
        Set<String> group = members.computeIfAbsent(canon, ignored -> new LinkedHashSet<>());
        for (String n : norms) {
            group.add(n);
            toCanonical.put(n, canon);
            members.putIfAbsent(n, group);
        }
    }
}
