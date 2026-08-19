package com.example.congraduation.roadmap.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 세종대 「동일과목조회」 학수번호 그룹.
 * 같은 엑셀 그룹에 함께 있는 과목만 동일 이수로 본다(겹치는 그룹을 합치지 않음).
 */
public final class RoadmapCourseCodeEquivalence {

    private static final String RESOURCE = "/roadmap/equivalent-course-groups.json";
    private static final Map<String, List<Integer>> GROUP_IDS_BY_CODE;
    private static final Map<Integer, Set<String>> CODES_BY_GROUP;
    private static final Map<Integer, Set<String>> NAMES_BY_GROUP;
    private static final Map<String, List<Integer>> GROUP_IDS_BY_NAME;

    static {
        GroupFile file = load();
        Map<String, List<Integer>> groupIdsByCode = new LinkedHashMap<>();
        Map<Integer, Set<String>> codesByGroup = new LinkedHashMap<>();
        Map<Integer, Set<String>> namesByGroup = new LinkedHashMap<>();
        Map<String, List<Integer>> groupIdsByName = new LinkedHashMap<>();

        for (GroupFile.Group group : file.groups()) {
            if (group == null || group.id() == null) {
                continue;
            }
            int id = group.id();
            Set<String> codes = new LinkedHashSet<>();
            Set<String> names = new LinkedHashSet<>();
            if (group.codes() != null) {
                for (String raw : group.codes()) {
                    String norm = normalize(raw);
                    if (norm.isBlank()) {
                        continue;
                    }
                    codes.add(norm);
                    groupIdsByCode.computeIfAbsent(norm, ignored -> new ArrayList<>()).add(id);
                }
            }
            if (group.names() != null) {
                for (String raw : group.names()) {
                    String key = normalizeName(raw);
                    if (key.isBlank()) {
                        continue;
                    }
                    names.add(raw.trim());
                    groupIdsByName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(id);
                }
            }
            codesByGroup.put(id, Collections.unmodifiableSet(codes));
            namesByGroup.put(id, Collections.unmodifiableSet(names));
        }

        GROUP_IDS_BY_CODE = freezeListMap(groupIdsByCode);
        CODES_BY_GROUP = Collections.unmodifiableMap(codesByGroup);
        NAMES_BY_GROUP = Collections.unmodifiableMap(namesByGroup);
        GROUP_IDS_BY_NAME = freezeListMap(groupIdsByName);
    }

    private RoadmapCourseCodeEquivalence() {
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String trimmed = code.trim();
        String stripped = trimmed.replaceFirst("^0+(?!$)", "");
        return stripped.toUpperCase(Locale.ROOT);
    }

    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 동등 그룹 대표 키. 그룹이 여러 개면 가장 작은 그룹번호. 그룹 없으면 자기 자신. */
    public static String canonical(String courseCode) {
        String norm = normalize(courseCode);
        if (norm.isBlank()) {
            return "";
        }
        List<Integer> ids = GROUP_IDS_BY_CODE.get(norm);
        if (ids == null || ids.isEmpty()) {
            return norm;
        }
        return "G" + ids.get(0);
    }

    public static List<Integer> groupIds(String courseCode) {
        String norm = normalize(courseCode);
        if (norm.isBlank()) {
            return List.of();
        }
        List<Integer> ids = GROUP_IDS_BY_CODE.get(norm);
        return ids == null ? List.of() : ids;
    }

    /**
     * 같은 엑셀 그룹에 함께 있으면 true.
     * 그룹이 겹치더라도 한 그룹에서 직접 만나지 않으면 false.
     */
    public static boolean shareGroup(String leftCode, String rightCode) {
        String left = normalize(leftCode);
        String right = normalize(rightCode);
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        List<Integer> leftIds = GROUP_IDS_BY_CODE.get(left);
        List<Integer> rightIds = GROUP_IDS_BY_CODE.get(right);
        if (leftIds == null || rightIds == null) {
            return false;
        }
        for (Integer id : leftIds) {
            if (rightIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean namesShareGroup(String leftName, String rightName) {
        String left = normalizeName(leftName);
        String right = normalizeName(rightName);
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        List<Integer> leftIds = GROUP_IDS_BY_NAME.get(left);
        List<Integer> rightIds = GROUP_IDS_BY_NAME.get(right);
        if (leftIds == null || rightIds == null) {
            return false;
        }
        for (Integer id : leftIds) {
            if (rightIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** 자기 자신 포함, 속한 모든 그룹의 학수번호(정규화). */
    public static List<String> equivalentsIncludingSelf(String courseCode) {
        String norm = normalize(courseCode);
        if (norm.isBlank()) {
            return List.of();
        }
        List<Integer> ids = GROUP_IDS_BY_CODE.get(norm);
        if (ids == null || ids.isEmpty()) {
            return List.of(norm);
        }
        Set<String> members = new LinkedHashSet<>();
        members.add(norm);
        for (Integer id : ids) {
            Set<String> codes = CODES_BY_GROUP.get(id);
            if (codes != null) {
                members.addAll(codes);
            }
        }
        return List.copyOf(members);
    }

    /** 속한 모든 그룹의 과목명(원문). */
    public static List<String> equivalentNames(String courseCode) {
        String norm = normalize(courseCode);
        if (norm.isBlank()) {
            return List.of();
        }
        List<Integer> ids = GROUP_IDS_BY_CODE.get(norm);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Integer id : ids) {
            Set<String> groupNames = NAMES_BY_GROUP.get(id);
            if (groupNames != null) {
                names.addAll(groupNames);
            }
        }
        return List.copyOf(names);
    }

    public static List<String> equivalentNamesForName(String courseName) {
        String key = normalizeName(courseName);
        if (key.isBlank()) {
            return List.of();
        }
        List<Integer> ids = GROUP_IDS_BY_NAME.get(key);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Integer id : ids) {
            Set<String> groupNames = NAMES_BY_GROUP.get(id);
            if (groupNames != null) {
                names.addAll(groupNames);
            }
        }
        return List.copyOf(names);
    }

    private static Map<String, List<Integer>> freezeListMap(Map<String, List<Integer>> source) {
        Map<String, List<Integer>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : source.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static GroupFile load() {
        try (InputStream in = RoadmapCourseCodeEquivalence.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("동일과목 리소스 없음: " + RESOURCE);
            }
            return new ObjectMapper().readValue(in, GroupFile.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("동일과목 리소스 로드 실패: " + RESOURCE, ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroupFile(List<Group> groups) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Group(Integer id, List<String> codes, List<String> names) {
        }
    }
}
