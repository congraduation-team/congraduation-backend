package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.service.AbeekDepartmentCatalog;
import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.exception.TranscriptNotFoundException;
import com.example.congraduation.roadmap.dto.RoadmapDepartmentDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.RoadmapCourseDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.RoadmapSummaryDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.SourceTermDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.TermRoadmapDto;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 강의시간표 기반 1~8학기 로드맵.
 * 이수 여부는 기이수성적 학수번호로 매칭한다.
 * 공학인증 대상 학과면 GENERAL/BSM/MAJOR로 나눠 반환한다.
 */
@Service
@RequiredArgsConstructor
public class StudentRoadmapService {

    private static final List<String> TERM_KEYS = List.of(
            "1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2"
    );

    private final TimetableCatalog timetableCatalog;
    private final AbeekDepartmentCatalog departmentCatalog;
    private final TranscriptStorageService transcriptStorageService;

    /**
     * 최신 강의시간표 기준 전 학과(개설학과) 목록. 학과명은 한글.
     * {@code departmentName} 또는 {@code aliases}를 {@link #getByDepartment}에 넘기면 된다.
     */
    @Transactional(readOnly = true)
    public List<RoadmapDepartmentDto> listDepartments() {
        return timetableCatalog.listOpeningDepartments().stream()
                .map(opening -> {
                    Optional<AbeekDepartmentCatalog.DepartmentInfo> abeek =
                            departmentCatalog.findByDepartmentName(opening.departmentName());
                    if (abeek.isEmpty()) {
                        for (String alias : shortNameAliases(opening.departmentName())) {
                            abeek = departmentCatalog.findByDepartmentName(alias);
                            if (abeek.isPresent()) {
                                break;
                            }
                        }
                    }
                    return RoadmapDepartmentDto.builder()
                            .departmentName(opening.departmentName())
                            .aliases(shortNameAliases(opening.departmentName()))
                            .college(opening.college())
                            .abeekTarget(abeek.isPresent())
                            .abeekDepartmentCode(abeek.map(AbeekDepartmentCatalog.DepartmentInfo::abeekCode).orElse(null))
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentRoadmapResponse getByStudent(Long studentDbId) {
        Student student = transcriptStorageService.getStudentOrThrow(studentDbId);
        Optional<AbeekDepartmentCatalog.DepartmentInfo> abeekDept =
                departmentCatalog.findByDepartmentName(student.getMajor());

        String departmentName = abeekDept.map(AbeekDepartmentCatalog.DepartmentInfo::name)
                .orElse(student.getMajor());
        boolean abeekTarget = abeekDept.isPresent();
        String abeekCode = abeekDept.map(AbeekDepartmentCatalog.DepartmentInfo::abeekCode).orElse(null);

        CompletionIndex completion = buildCompletionIndex(studentDbId);
        return buildRoadmap(
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                departmentName,
                abeekTarget,
                abeekCode,
                openingNamesFor(departmentName, abeekDept),
                completion,
                student.getAdmissionYear()
        );
    }

    @Transactional(readOnly = true)
    public StudentRoadmapResponse getByDepartment(String departmentName, Long studentDbId) {
        if (departmentName == null || departmentName.isBlank()) {
            throw new IllegalArgumentException("departmentName이 필요합니다.");
        }
        Optional<AbeekDepartmentCatalog.DepartmentInfo> abeekDept =
                departmentCatalog.findByDepartmentName(departmentName.trim());
        boolean abeekTarget = abeekDept.isPresent();
        String resolvedName = abeekDept.map(AbeekDepartmentCatalog.DepartmentInfo::name)
                .orElse(departmentName.trim());
        String abeekCode = abeekDept.map(AbeekDepartmentCatalog.DepartmentInfo::abeekCode).orElse(null);

        Student student = null;
        CompletionIndex completion = CompletionIndex.empty();
        Integer admissionYear = null;
        if (studentDbId != null) {
            student = transcriptStorageService.getStudentOrThrow(studentDbId);
            completion = buildCompletionIndex(studentDbId);
            admissionYear = student.getAdmissionYear();
        }

        return buildRoadmap(
                student == null ? null : student.getId(),
                student == null ? null : student.getStudentNo(),
                student == null ? null : student.getName(),
                resolvedName,
                abeekTarget,
                abeekCode,
                openingNamesFor(resolvedName, abeekDept),
                completion,
                admissionYear
        );
    }

    private StudentRoadmapResponse buildRoadmap(
            Long studentDbId,
            String studentNo,
            String studentName,
            String departmentName,
            boolean abeekTarget,
            String abeekCode,
            Set<String> openingNames,
            CompletionIndex completion,
            Integer admissionYear
    ) {
        List<TimetableTermData> sourceTerms = resolveSourceTerms();
        Map<String, Map<String, AggregatedCourse>> byTermAndCode = new LinkedHashMap<>();
        for (String termKey : TERM_KEYS) {
            byTermAndCode.put(termKey, new LinkedHashMap<>());
        }

        List<SourceTermDto> sourceDtos = new ArrayList<>();
        for (TimetableTermData term : sourceTerms) {
            int used = 0;
            for (TimetableOffering offering : term.offerings()) {
                if (!shouldIncludeOffering(offering, openingNames)) {
                    continue;
                }
                Integer gradeYear = parseGradeYear(offering.gradeYear());
                if (gradeYear == null || gradeYear < 1 || gradeYear > 4) {
                    continue;
                }
                String termKey = gradeYear + "-" + term.semester();
                if (!byTermAndCode.containsKey(termKey)) {
                    continue;
                }
                String code = offering.courseCode() == null ? "" : offering.courseCode().trim();
                if (code.isBlank()) {
                    continue;
                }
                AggregatedCourse agg = byTermAndCode.get(termKey)
                        .computeIfAbsent(code, ignored -> AggregatedCourse.from(offering));
                agg.sectionCount++;
                used++;
            }
            sourceDtos.add(SourceTermDto.builder()
                    .termYear(term.termYear())
                    .semester(term.semester())
                    .offeringCount(used)
                    .build());
        }

        // 이수한 과목은 시간표 권장학기가 아니라 실제 이수학기에 하나만 배치
        // (예: Capstone이 4-1·4-2에 둘 다 있어도, 3-2에 들었으면 3-2에만 표시)
        relocateCompletedCourses(byTermAndCode, completion, admissionYear);
        // 학과 개설 시간표에 없는 교양필수·기필 등 이수 과목도 실제 이수학기에 표시
        injectMissingCompletedCourses(byTermAndCode, completion, admissionYear);

        List<TermRoadmapDto> terms = new ArrayList<>();
        List<RoadmapCourseDto> allCourses = new ArrayList<>();
        for (int i = 0; i < TERM_KEYS.size(); i++) {
            String termKey = TERM_KEYS.get(i);
            String[] parts = termKey.split("-");
            List<RoadmapCourseDto> courses = byTermAndCode.get(termKey).values().stream()
                    .sorted(Comparator.comparing(AggregatedCourse::courseName, Comparator.nullsLast(String::compareTo)))
                    .map(agg -> toCourseDto(agg, completion))
                    .toList();
            allCourses.addAll(courses);

            Map<String, List<RoadmapCourseDto>> categories = null;
            if (abeekTarget) {
                categories = new LinkedHashMap<>();
                categories.put("GENERAL", filterBucket(courses, "GENERAL"));
                categories.put("BSM", filterBucket(courses, "BSM"));
                categories.put("MAJOR", filterBucket(courses, "MAJOR"));
            }

            terms.add(TermRoadmapDto.builder()
                    .termKey(termKey)
                    .gradeYear(Integer.parseInt(parts[0]))
                    .semester(Integer.parseInt(parts[1]))
                    .termIndex(i + 1)
                    .courses(courses)
                    .categories(categories)
                    .build());
        }

        return StudentRoadmapResponse.builder()
                .studentDbId(studentDbId)
                .studentNo(studentNo)
                .studentName(studentName)
                .departmentName(departmentName)
                .abeekTarget(abeekTarget)
                .abeekDepartmentCode(abeekCode)
                .sourceTerms(sourceDtos)
                .terms(terms)
                .summary(buildSummary(allCourses, abeekTarget))
                .build();
    }

    private List<TimetableTermData> resolveSourceTerms() {
        TimetableTermData spring = timetableCatalog.latestTermForSemester(1)
                .orElseThrow(() -> new IllegalArgumentException("1학기 강의시간표 데이터가 없습니다."));
        TimetableTermData fall = timetableCatalog.latestTermForSemester(2)
                .orElseThrow(() -> new IllegalArgumentException("2학기 강의시간표 데이터가 없습니다."));
        // 1학기·2학기 모두 사용해 1-1~4-2 채움
        return List.of(spring, fall);
    }

    /**
     * 이수한 학수번호는 로드맵에서 한 번만, 실제 이수학기(학년-학기) 칸에 둔다.
     * Capstone·생성형AI처럼 1·2학기 시간표에 모두 있는 과목이 4-1/4-2에 중복 표시되는 것을 막는다.
     */
    private void relocateCompletedCourses(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Integer admissionYear
    ) {
        if (admissionYear == null || completion.isEmpty()) {
            return;
        }

        Map<String, AggregatedCourse> completedCourses = new LinkedHashMap<>();
        Map<String, CompletionHit> hitsByNormCode = new LinkedHashMap<>();
        Map<String, String> fallbackTermByNormCode = new LinkedHashMap<>();

        for (String termKey : TERM_KEYS) {
            Map<String, AggregatedCourse> termMap = byTermAndCode.get(termKey);
            var iterator = termMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AggregatedCourse> entry = iterator.next();
                CompletionHit hit = completion.findByCourseCode(entry.getKey());
                if (hit == null) {
                    continue;
                }
                String norm = normalizeCourseCode(entry.getKey());
                completedCourses.putIfAbsent(norm, entry.getValue());
                hitsByNormCode.putIfAbsent(norm, hit);
                fallbackTermByNormCode.putIfAbsent(norm, termKey);
                iterator.remove();
            }
        }

        for (Map.Entry<String, AggregatedCourse> entry : completedCourses.entrySet()) {
            String norm = entry.getKey();
            CompletionHit hit = hitsByNormCode.get(norm);
            String targetTerm = resolveTakenTermKey(admissionYear, hit.year(), hit.semester());
            if (targetTerm == null || !byTermAndCode.containsKey(targetTerm)) {
                targetTerm = fallbackTermByNormCode.getOrDefault(norm, TERM_KEYS.get(0));
            }
            AggregatedCourse course = entry.getValue();
            byTermAndCode.get(targetTerm).put(course.courseCode, course);
        }
    }

    /**
     * 학과 시간표에 없는 이수 과목(교양필수·기필·학문기초 등)을 실제 이수학기 칸에 추가한다.
     */
    private void injectMissingCompletedCourses(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Integer admissionYear
    ) {
        if (completion.isEmpty()) {
            return;
        }

        Set<String> present = new HashSet<>();
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            for (String code : termMap.keySet()) {
                present.add(normalizeCourseCode(code));
            }
        }

        for (CompletionHit hit : completion.allHits()) {
            String norm = normalizeCourseCode(hit.courseCode());
            if (norm.isBlank() || present.contains(norm)) {
                continue;
            }
            if (!isRoadmapRelevantCompletedCategory(hit.category())) {
                continue;
            }

            String targetTerm = admissionYear == null
                    ? null
                    : resolveTakenTermKey(admissionYear, hit.year(), hit.semester());
            if (targetTerm == null || !byTermAndCode.containsKey(targetTerm)) {
                Integer semester = toRoadmapSemester(hit.semester());
                targetTerm = (semester != null && semester == 2) ? "1-2" : "1-1";
            }

            AggregatedCourse course = AggregatedCourse.fromCompleted(
                    hit.courseCode(),
                    hit.courseName(),
                    hit.category(),
                    hit.credits()
            );
            byTermAndCode.get(targetTerm).putIfAbsent(course.courseCode, course);
            present.add(norm);
        }
    }

    /** 공통 로드맵에 올릴 이수 과목 구분 (기초·교양·전공 관련) */
    private boolean isRoadmapRelevantCompletedCategory(String category) {
        if (category == null || category.isBlank()) {
            // 구분 없으면 일단 표시 (기이수에 구분이 비는 경우)
            return true;
        }
        String cat = category.replaceAll("\\s+", "");
        return cat.contains("교양필수")
                || cat.contains("기초필수")
                || cat.contains("학문기초")
                || cat.contains("전공기초")
                || cat.contains("교필")
                || cat.contains("공필")
                || cat.contains("기필")
                || cat.contains("균필")
                || cat.contains("전필")
                || cat.contains("전선")
                || cat.contains("전기")
                || cat.contains("전공필수")
                || cat.contains("전공선택")
                || cat.contains("전공")
                || cat.contains("교양")
                || cat.contains("중핵")
                || cat.contains("BSM")
                || cat.contains("MSC");
    }

    /**
     * 입학년도 + 수강연도/학기 → 로드맵 termKey (예: 2021입학, 2023년 2학기 → "3-2").
     * 4학년을 넘으면 4학년 해당 학기로 clamp.
     */
    String resolveTakenTermKey(int admissionYear, String takenYear, String takenSemester) {
        Integer year = parseIntOrNull(takenYear);
        Integer semester = toRoadmapSemester(takenSemester);
        if (year == null || semester == null) {
            return null;
        }
        int gradeYear = year - admissionYear + 1;
        if (gradeYear < 1) {
            gradeYear = 1;
        } else if (gradeYear > 4) {
            gradeYear = 4;
        }
        return gradeYear + "-" + semester;
    }

    private Integer toRoadmapSemester(String semesterText) {
        if (semesterText == null || semesterText.isBlank()) {
            return null;
        }
        String normalized = semesterText.trim();
        if (normalized.contains("2") || normalized.contains("겨울")) {
            return 2;
        }
        if (normalized.contains("1") || normalized.contains("여름")) {
            return 1;
        }
        return null;
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String digits = value.trim().replaceAll("[^0-9]", "");
            if (digits.isBlank()) {
                return null;
            }
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Set<String> openingNamesFor(
            String departmentName,
            Optional<AbeekDepartmentCatalog.DepartmentInfo> abeekDept
    ) {
        Set<String> names = new HashSet<>();
        names.add(normalize(departmentName));
        for (String alias : shortNameAliases(departmentName)) {
            names.add(normalize(alias));
        }
        // 시간표는 "창의소프트학부 디자인이노베이션전공"처럼 학부+전공인 경우가 많음
        for (TimetableCatalog.OpeningDepartment opening : timetableCatalog.listOpeningDepartments()) {
            String openingName = opening.departmentName();
            if (fuzzyDepartmentMatch(normalize(departmentName), normalize(openingName))) {
                names.add(normalize(openingName));
            }
        }
        abeekDept.ifPresent(info -> {
            for (String alias : departmentCatalog.openingDepartmentNames(info.abeekCode())) {
                names.add(normalize(alias));
            }
            names.add(normalize(info.name()));
        });
        return names;
    }

    /**
     * 학생 전공명(디자인이노베이션전공) ↔ 시간표 개설학과(창의소프트학부 디자인이노베이션전공) 매칭.
     * OfferedCurriculumService와 동일하게 포함 매칭을 허용한다.
     */
    private boolean matchesOpeningDepartment(TimetableOffering offering, Set<String> openingNames) {
        if (openingNames.isEmpty()) {
            return false;
        }
        String opening = normalize(offering.openingDepartment());
        String host = normalize(offering.hostDepartment());
        for (String name : openingNames) {
            if (name.isBlank()) {
                continue;
            }
            if (fuzzyDepartmentMatch(name, opening) || fuzzyDepartmentMatch(name, host)) {
                return true;
            }
        }
        return false;
    }

    /** 학과 개설 과목 + 전학생 공통 교양필수/기초필수 */
    private boolean shouldIncludeOffering(TimetableOffering offering, Set<String> openingNames) {
        if (matchesOpeningDepartment(offering, openingNames)) {
            return true;
        }
        return isCommonRequiredOffering(offering);
    }

    /**
     * 대양휴머니티 등 전학생 공통 교양필수만 학과와 무관하게 포함한다.
     * 확률및통계·선형대수 같은 전공기초는 해당 학과 개설일 때만 표시한다.
     */
    private boolean isCommonRequiredOffering(TimetableOffering offering) {
        String cat = offering.category() == null ? "" : offering.category().replaceAll("\\s+", "");
        if (cat.contains("교양필수") || cat.contains("기초필수") || cat.contains("학문기초")) {
            return true;
        }
        String opening = offering.openingDepartment() == null ? "" : offering.openingDepartment();
        return opening.contains("대양휴머니티") && (cat.contains("필수") || cat.contains("중핵"));
    }

    private boolean fuzzyDepartmentMatch(String query, String candidate) {
        if (query == null || query.isBlank() || candidate == null || candidate.isBlank()) {
            return false;
        }
        return candidate.equals(query) || candidate.contains(query) || query.contains(candidate);
    }

    /** "창의소프트학부 디자인이노베이션전공" → ["디자인이노베이션전공"] */
    private List<String> shortNameAliases(String openingName) {
        if (openingName == null || openingName.isBlank()) {
            return List.of();
        }
        String trimmed = openingName.trim();
        int space = trimmed.lastIndexOf(' ');
        if (space <= 0 || space >= trimmed.length() - 1) {
            return List.of();
        }
        String suffix = trimmed.substring(space + 1).trim();
        if (suffix.isBlank() || suffix.equals(trimmed)) {
            return List.of();
        }
        return List.of(suffix);
    }

    private RoadmapCourseDto toCourseDto(AggregatedCourse agg, CompletionIndex completion) {
        CompletionHit hit = completion.findByCourseCode(agg.courseCode);
        String bucket = classifyAbeekBucket(agg.category, agg.courseName);
        return RoadmapCourseDto.builder()
                .courseCode(agg.courseCode)
                .courseName(agg.courseName)
                .category(agg.category)
                .abeekBucket(bucket)
                .credits(agg.credits)
                .completed(hit != null)
                .takenYear(hit == null ? null : hit.year())
                .takenSemester(hit == null ? null : hit.semester())
                .grade(hit == null ? null : hit.grade())
                .sectionCount(agg.sectionCount)
                .build();
    }

    private List<RoadmapCourseDto> filterBucket(List<RoadmapCourseDto> courses, String bucket) {
        return courses.stream().filter(c -> bucket.equals(c.getAbeekBucket())).toList();
    }

    /**
     * 시간표 이수구분/과목명으로 공학인증 표시 버킷 추정.
     * 학과 개설 시간표에는 전공이 대부분이라 GENERAL/BSM은 적을 수 있다.
     */
    String classifyAbeekBucket(String category, String courseName) {
        String cat = category == null ? "" : category.replaceAll("\\s+", "");
        String name = courseName == null ? "" : courseName.replaceAll("\\s+", "");

        if (cat.contains("BSM") || name.contains("BSM")) {
            return "BSM";
        }
        if (cat.contains("교양필수") || cat.contains("기초필수") || cat.contains("학문기초")
                || cat.contains("전문교양") || (cat.contains("MSC") && !cat.contains("전공"))) {
            return "GENERAL";
        }
        // 전형적 BSM / 기초 과목명 (전공기초로 개설돼도 BSM으로 표시)
        if (name.contains("미적분") || name.contains("공업수학") || name.contains("이산수학")
                || name.contains("선형대수") || (name.contains("확률") && name.contains("통계"))
                || name.contains("일반물리") || name.contains("물리학")
                || name.contains("일반화학") || name.contains("일반생물")) {
            return "BSM";
        }
        if (cat.contains("교양") || cat.contains("중핵") || cat.contains("공통")
                || cat.contains("교필") || cat.contains("공필") || cat.contains("기필")) {
            return "GENERAL";
        }
        if (cat.contains("전공") || cat.contains("전필") || cat.contains("전선")
                || cat.contains("전공기초") || cat.contains("전기") || cat.contains("기초")) {
            return "MAJOR";
        }
        return "OTHER";
    }

    private RoadmapSummaryDto buildSummary(List<RoadmapCourseDto> courses, boolean abeekTarget) {
        int completed = 0;
        int general = 0;
        int bsm = 0;
        int major = 0;
        for (RoadmapCourseDto course : courses) {
            if (course.isCompleted()) {
                completed++;
            }
            if (abeekTarget) {
                switch (course.getAbeekBucket()) {
                    case "GENERAL" -> general++;
                    case "BSM" -> bsm++;
                    case "MAJOR" -> major++;
                    default -> {
                    }
                }
            }
        }
        return RoadmapSummaryDto.builder()
                .totalCourses(courses.size())
                .completedCourses(completed)
                .generalCount(general)
                .bsmCount(bsm)
                .majorCount(major)
                .build();
    }

    private CompletionIndex buildCompletionIndex(Long studentDbId) {
        List<CompletedCourseUploadRowDto> rows;
        try {
            rows = transcriptStorageService.getLatestTranscriptRows(studentDbId);
        } catch (TranscriptNotFoundException ex) {
            return CompletionIndex.empty();
        }
        Map<String, CompletionHit> byCode = new LinkedHashMap<>();
        for (CompletedCourseUploadRowDto row : rows) {
            if (!isPassed(row.grade())) {
                continue;
            }
            String key = normalizeCourseCode(row.courseCode());
            if (key.isBlank()) {
                continue;
            }
            byCode.putIfAbsent(key, new CompletionHit(
                    row.year(),
                    row.semester(),
                    row.grade(),
                    row.courseCode() == null ? "" : row.courseCode().trim(),
                    row.courseName(),
                    row.category(),
                    parseCredits(row.credit())
            ));
        }
        return new CompletionIndex(byCode);
    }

    private Double parseCredits(String credit) {
        if (credit == null || credit.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(credit.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isPassed(String grade) {
        if (grade == null || grade.isBlank()) {
            return true;
        }
        String normalized = grade.trim().toUpperCase(Locale.ROOT);
        return !(normalized.equals("F") || normalized.equals("NP") || normalized.equals("N") || normalized.equals("U"));
    }

    private Integer parseGradeYear(String gradeYear) {
        if (gradeYear == null || gradeYear.isBlank()) {
            return null;
        }
        String digits = gradeYear.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits.substring(0, 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    static String normalizeCourseCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String trimmed = code.trim();
        String stripped = trimmed.replaceFirst("^0+(?!$)", "");
        return stripped.toUpperCase(Locale.ROOT);
    }

    private static final class AggregatedCourse {
        private final String courseCode;
        private final String courseName;
        private final String category;
        private final Double credits;
        private int sectionCount;

        private AggregatedCourse(String courseCode, String courseName, String category, Double credits) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.category = category;
            this.credits = credits;
            this.sectionCount = 0;
        }

        static AggregatedCourse from(TimetableOffering offering) {
            return new AggregatedCourse(
                    offering.courseCode() == null ? "" : offering.courseCode().trim(),
                    offering.courseName(),
                    offering.category(),
                    offering.credits()
            );
        }

        static AggregatedCourse fromCompleted(String courseCode, String courseName, String category, Double credits) {
            AggregatedCourse course = new AggregatedCourse(
                    courseCode == null ? "" : courseCode.trim(),
                    courseName,
                    category,
                    credits
            );
            course.sectionCount = 1;
            return course;
        }

        String courseName() {
            return courseName;
        }
    }

    private record CompletionHit(
            String year,
            String semester,
            String grade,
            String courseCode,
            String courseName,
            String category,
            Double credits
    ) {
    }

    private record CompletionIndex(Map<String, CompletionHit> byNormalizedCode) {
        static CompletionIndex empty() {
            return new CompletionIndex(Map.of());
        }

        boolean isEmpty() {
            return byNormalizedCode == null || byNormalizedCode.isEmpty();
        }

        CompletionHit findByCourseCode(String courseCode) {
            return byNormalizedCode.get(normalizeCourseCode(courseCode));
        }

        Collection<CompletionHit> allHits() {
            return byNormalizedCode.values();
        }
    }
}
