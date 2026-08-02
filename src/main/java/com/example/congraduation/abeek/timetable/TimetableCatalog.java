package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimetableCatalog {

    private final ObjectMapper objectMapper;
    private final List<TimetableTermData> terms = new ArrayList<>();
    /** 정규화 과목명 → 최신 학기 기준 학수번호 */
    private final Map<String, String> courseCodeByNormalizedName = new HashMap<>();

    @PostConstruct
    void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:timetable-data/*.json");
        for (Resource resource : resources) {
            TimetableTermData data = objectMapper.readValue(resource.getInputStream(), TimetableTermData.class);
            terms.add(data);
            log.info("Loaded timetable {}-{} ({} offerings)",
                    data.termYear(), data.semester(), data.offerings() == null ? 0 : data.offerings().size());
        }
        terms.sort(Comparator
                .comparingInt(TimetableTermData::termYear).reversed()
                .thenComparingInt(TimetableTermData::semester).reversed());
        indexCourseCodesByName();
    }

    private void indexCourseCodesByName() {
        // 오래된 학기부터 넣어 최신 학기 코드가 덮어쓰게 한다.
        List<TimetableTermData> oldestFirst = new ArrayList<>(terms);
        Collections.reverse(oldestFirst);
        for (TimetableTermData term : oldestFirst) {
            if (term.offerings() == null) {
                continue;
            }
            for (TimetableOffering offering : term.offerings()) {
                String code = offering.courseCode() == null ? "" : offering.courseCode().trim();
                String name = normalizeCourseName(offering.courseName());
                if (code.isBlank() || name.isBlank()) {
                    continue;
                }
                courseCodeByNormalizedName.put(name, code);
            }
        }
    }

    /** 강의시간표 기준 과목명 → 학수번호 (공백 무시 정규화). */
    public Optional<String> findCourseCodeByName(String courseName) {
        String key = normalizeCourseName(courseName);
        if (key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(courseCodeByNormalizedName.get(key));
    }

    static String normalizeCourseName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.replaceAll("\\s+", "");
    }

    public List<TimetableTermData> availableTerms() {
        return List.copyOf(terms);
    }

    public Optional<TimetableTermData> findTerm(int termYear, int semester) {
        return terms.stream()
                .filter(term -> term.termYear() == termYear && term.semester() == semester)
                .findFirst();
    }

    public Optional<TimetableTermData> latestTerm() {
        return terms.stream().findFirst();
    }

    /** 해당 학기(1/2)의 가장 최근 시간표 */
    public Optional<TimetableTermData> latestTermForSemester(int semester) {
        return terms.stream()
                .filter(term -> term.semester() == semester)
                .findFirst();
    }

    /**
     * 최신 1·2학기 시간표에 등장하는 개설학과(한글명) 목록.
     * college는 해당 개설학과에서 가장 많이 나온 단과대학.
     */
    public List<OpeningDepartment> listOpeningDepartments() {
        List<TimetableTermData> source = new ArrayList<>();
        latestTermForSemester(1).ifPresent(source::add);
        latestTermForSemester(2).ifPresent(source::add);
        if (source.isEmpty()) {
            source.addAll(terms);
        }

        Map<String, Map<String, Integer>> collegeCounts = new LinkedHashMap<>();
        for (TimetableTermData term : source) {
            if (term.offerings() == null) {
                continue;
            }
            for (TimetableOffering offering : term.offerings()) {
                String name = offering.openingDepartment() == null ? "" : offering.openingDepartment().trim();
                if (name.isBlank()) {
                    continue;
                }
                String college = offering.college() == null ? "" : offering.college().trim();
                collegeCounts
                        .computeIfAbsent(name, ignored -> new HashMap<>())
                        .merge(college, 1, Integer::sum);
            }
        }

        return collegeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new OpeningDepartment(
                        entry.getKey(),
                        mostFrequentCollege(entry.getValue())
                ))
                .toList();
    }

    private static String mostFrequentCollege(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public record OpeningDepartment(String departmentName, String college) {
    }
}
