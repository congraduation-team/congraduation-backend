package com.example.congraduation.service.graduation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 공통교양 과목명 개편 동일과목.
 * 신입생세미나 → 세종인을위한진로설계, 대학생활과진로탐색 → 세종인을위한전공탐색.
 */
public final class LiberalCourseRenameEquivalence {

    private static final Set<String> CAREER_DESIGN_NAMES = names(
            "세종인을위한진로설계",
            "신입생세미나",
            "신입생세미나1",
            "신입생세미나A"
    );

    private static final Set<String> MAJOR_EXPLORATION_NAMES = names(
            "세종인을위한전공탐색",
            "신입생세미나B",
            "신입생세미나2",
            "대학생활과진로탐색",
            "대학생활과 진로탐색",
            "대학생활과진로설계",
            "대학생활과 진로설계",
            "대학생활과진로설계1",
            "대학생활과 진로설계1"
    );

    private static final Set<String> CAREER_DESIGN_KEYS = comparableSet(CAREER_DESIGN_NAMES);
    private static final Set<String> MAJOR_EXPLORATION_KEYS = comparableSet(MAJOR_EXPLORATION_NAMES);

    private LiberalCourseRenameEquivalence() {
    }

    public static Set<String> careerDesignNames() {
        return CAREER_DESIGN_NAMES;
    }

    public static Set<String> majorExplorationNames() {
        return MAJOR_EXPLORATION_NAMES;
    }

    public static Set<String> familyOf(String courseName) {
        String key = comparable(courseName);
        if (key.isBlank()) {
            return Set.of();
        }
        if (CAREER_DESIGN_KEYS.contains(key)) {
            return CAREER_DESIGN_NAMES;
        }
        if (MAJOR_EXPLORATION_KEYS.contains(key)) {
            return MAJOR_EXPLORATION_NAMES;
        }
        return Set.of();
    }

    public static boolean sameFamily(String left, String right) {
        String leftKey = comparable(left);
        String rightKey = comparable(right);
        if (leftKey.isBlank() || rightKey.isBlank()) {
            return false;
        }
        if (CAREER_DESIGN_KEYS.contains(leftKey) && CAREER_DESIGN_KEYS.contains(rightKey)) {
            return true;
        }
        return MAJOR_EXPLORATION_KEYS.contains(leftKey) && MAJOR_EXPLORATION_KEYS.contains(rightKey);
    }

    private static Set<String> names(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(values)));
    }

    private static Set<String> comparableSet(Set<String> names) {
        Set<String> keys = new LinkedHashSet<>();
        for (String name : names) {
            keys.add(comparable(name));
        }
        return Set.copyOf(keys);
    }

    private static String comparable(String courseName) {
        return courseName == null ? "" : courseName.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }
}
