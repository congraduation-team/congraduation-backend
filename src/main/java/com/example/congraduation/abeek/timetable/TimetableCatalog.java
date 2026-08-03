package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TimetableCatalog {

    private static final Pattern TERM_FILE_NAME = Pattern.compile("^(\\d{4})-([1-4])\\.json$", Pattern.CASE_INSENSITIVE);
    /** 봄·가을 시간표가 사실상 동일 내용일 때(잘못된 업로드) 판정 임계값 */
    static final double DUPLICATE_TERM_JACCARD = 0.90;

    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final List<TimetableTermData> terms = new ArrayList<>();
    /** 정규화 과목명 → 최신 학기 기준 학수번호 */
    private final Map<String, String> courseCodeByNormalizedName = new HashMap<>();

    public TimetableCatalog(
            ObjectMapper objectMapper,
            @Value("${app.timetable.data-dir:./data/timetable-data}") String dataDir
    ) {
        this.objectMapper = objectMapper;
        this.dataDir = Path.of(dataDir);
    }

    @PostConstruct
    void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:timetable-data/*.json");
        for (Resource resource : resources) {
            TimetableTermData data = objectMapper.readValue(resource.getInputStream(), TimetableTermData.class);
            data = alignTermIdentity(resource.getFilename(), data);
            putTerm(data);
            log.info("Loaded timetable {}-{} ({} offerings) from classpath",
                    data.termYear(), data.semester(), data.offerings() == null ? 0 : data.offerings().size());
        }
        loadExternalOverrides();
        sortTerms();
        indexCourseCodesByName();
    }

    private void loadExternalOverrides() throws IOException {
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.json")) {
            for (Path path : stream) {
                TimetableTermData data = objectMapper.readValue(path.toFile(), TimetableTermData.class);
                data = alignTermIdentity(path.getFileName().toString(), data);
                int incoming = data.offerings() == null ? 0 : data.offerings().size();
                if (incoming == 0) {
                    log.warn("Skip empty timetable override {}", path.toAbsolutePath());
                    continue;
                }
                Optional<TimetableTermData> existing = findTerm(data.termYear(), data.semester());
                if (existing.isPresent()) {
                    int current = existing.get().offerings() == null ? 0 : existing.get().offerings().size();
                    //  sparseness guard: 관리자 업로드 실패/부분 파일로 풍부한 classpath 데이터를 지우지 않음
                    if (current > 0 && incoming * 2 < current) {
                        log.warn(
                                "Skip sparse timetable override {} ({} offerings) over existing {}-{} ({} offerings)",
                                path.toAbsolutePath(),
                                incoming,
                                data.termYear(),
                                data.semester(),
                                current
                        );
                        continue;
                    }
                }
                // 가을 시간표를 봄으로(또는 그 반대) 잘못 올린 경우 classpath 정상 데이터를 덮어쓰지 않음
                if (isCrossSemesterDuplicateOverride(data)) {
                    log.warn(
                            "Skip cross-semester duplicate timetable override {} ({}-{} looks like a copy of the other semester)",
                            path.toAbsolutePath(),
                            data.termYear(),
                            data.semester()
                    );
                    continue;
                }
                putTerm(data);
                log.info("Loaded timetable {}-{} ({} offerings) from {}",
                        data.termYear(),
                        data.semester(),
                        incoming,
                        path.toAbsolutePath());
            }
        }
    }

    /**
     * 파일명 {@code YYYY-S.json}이 있으면 JSON의 termYear/semester보다 파일명을 신뢰한다.
     * (예: 2026-2.json 내용에 semester=1이 있어 봄 슬롯을 덮어쓰는 사고 방지)
     */
    public static TimetableTermData alignTermIdentity(String fileName, TimetableTermData data) {
        if (fileName == null || data == null) {
            return data;
        }
        Matcher matcher = TERM_FILE_NAME.matcher(fileName.trim());
        if (!matcher.matches()) {
            return data;
        }
        int year = Integer.parseInt(matcher.group(1));
        int semester = Integer.parseInt(matcher.group(2));
        if (data.termYear() == year && data.semester() == semester) {
            return data;
        }
        log.warn(
                "Correcting timetable identity from filename {}: JSON was {}-{}, using {}-{}",
                fileName,
                data.termYear(),
                data.semester(),
                year,
                semester
        );
        return new TimetableTermData(year, semester, data.offerings());
    }

    private boolean isCrossSemesterDuplicateOverride(TimetableTermData incoming) {
        int otherSemester = incoming.semester() == 1 ? 2 : incoming.semester() == 2 ? 1 : -1;
        if (otherSemester < 0) {
            return false;
        }
        Optional<TimetableTermData> other = findTerm(incoming.termYear(), otherSemester);
        if (other.isEmpty()) {
            other = latestTermForSemester(otherSemester);
        }
        return other.filter(term -> isNearDuplicateTerm(incoming, term)).isPresent();
    }

    /** 학수번호 집합 Jaccard 유사도로 두 학기 시간표가 사실상 동일 내용인지 판정. */
    public static boolean isNearDuplicateTerm(TimetableTermData left, TimetableTermData right) {
        if (left == null || right == null) {
            return false;
        }
        Set<String> leftCodes = offeringCodes(left);
        Set<String> rightCodes = offeringCodes(right);
        if (leftCodes.isEmpty() || rightCodes.isEmpty()) {
            return false;
        }
        int intersection = 0;
        for (String code : leftCodes) {
            if (rightCodes.contains(code)) {
                intersection++;
            }
        }
        int union = leftCodes.size() + rightCodes.size() - intersection;
        if (union <= 0) {
            return false;
        }
        return (intersection / (double) union) >= DUPLICATE_TERM_JACCARD;
    }

    static Set<String> offeringCodes(TimetableTermData term) {
        Set<String> codes = new HashSet<>();
        if (term == null || term.offerings() == null) {
            return codes;
        }
        for (TimetableOffering offering : term.offerings()) {
            String code = offering.courseCode() == null ? "" : offering.courseCode().trim();
            if (code.isBlank()) {
                continue;
            }
            codes.add(code.toUpperCase(Locale.ROOT));
        }
        return codes;
    }

    /** 업로드된 학기 데이터를 메모리에 즉시 반영한다. */
    public synchronized void replaceTerm(TimetableTermData data) {
        putTerm(data);
        sortTerms();
        courseCodeByNormalizedName.clear();
        indexCourseCodesByName();
        log.info("Replaced in-memory timetable {}-{} ({} offerings)",
                data.termYear(), data.semester(), data.offerings() == null ? 0 : data.offerings().size());
    }

    private void putTerm(TimetableTermData data) {
        terms.removeIf(term -> term.termYear() == data.termYear() && term.semester() == data.semester());
        terms.add(data);
    }

    private void sortTerms() {
        terms.sort(Comparator
                .comparingInt(TimetableTermData::termYear)
                .thenComparingInt(TimetableTermData::semester)
                .reversed());
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
