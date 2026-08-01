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
import com.example.congraduation.service.transcript.TranscriptStandingMapper;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

        // 기이수: 입학 이후 정규학기 순번으로 재배치. 학기 없으면 넣지 않음.
        placeCompletedCoursesByStanding(byTermAndCode, completion, admissionYear);

        // 학생 로드맵: 교양(GENERAL)은 기이수만. 시간표 공통교양필수 미이수 슬롯 제거.
        if (studentDbId != null) {
            stripIncompleteGeneralCourses(byTermAndCode, completion);
        }

        List<TermRoadmapDto> terms = new ArrayList<>();
        List<RoadmapCourseDto> allCourses = new ArrayList<>();
        for (int i = 0; i < TERM_KEYS.size(); i++) {
            String termKey = TERM_KEYS.get(i);
            String[] parts = termKey.split("-");
            List<RoadmapCourseDto> courses = byTermAndCode.get(termKey).values().stream()
                    .sorted(Comparator.comparing(AggregatedCourse::courseName, Comparator.nullsLast(String::compareTo)))
                    .map(agg -> toCourseDto(agg, completion, termKey))
                    .toList();
            allCourses.addAll(courses);

            Map<String, List<RoadmapCourseDto>> categories = new LinkedHashMap<>();
            // 교양 행: 전공·BSM이 아닌 기이수 (공필/교선/균필 등)
            List<RoadmapCourseDto> general = filterBucket(courses, "GENERAL");
            if (studentDbId != null) {
                general = general.stream().filter(RoadmapCourseDto::isCompleted).toList();
            }
            categories.put("GENERAL", general);
            categories.put("FOUNDATION", filterByDisplayCategory(courses, "기초필수"));
            categories.put("MAJOR", filterBucket(courses, "MAJOR"));
            if (abeekTarget) {
                categories.put("BSM", filterBucket(courses, "BSM"));
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
                .summary(buildSummary(allCourses, true))
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

    private boolean shouldIncludeOffering(TimetableOffering offering, Set<String> openingNames) {
        if (matchesOpeningDepartment(offering, openingNames)) {
            return true;
        }
        return isCommonRequiredOffering(offering);
    }

    /**
     * 전학생 공통으로 시간표에서 넣는 것: 기초필수·학문기초만.
     * 교양필수·자기주도창의전공은 기이수(placeCompleted)로만 넣는다.
     * (자기주도 Ⅲ·Ⅳ 등이 1학년 칸에 무분별하게 붙는 것 방지)
     */
    private boolean isCommonRequiredOffering(TimetableOffering offering) {
        String cat = offering.category() == null ? "" : offering.category().replaceAll("\\s+", "");
        return cat.contains("기초필수") || cat.contains("학문기초");
    }

    /**
     * 학생 로드맵에서 미이수 교양(GENERAL) 시간표 슬롯을 제거한다.
     * English Listening/Reading 등 공통 교양필수가 미이수로 남는 것을 막는다.
     */
    private void stripIncompleteGeneralCourses(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion
    ) {
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            var iterator = termMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AggregatedCourse> entry = iterator.next();
                if (completion.findByCourseCode(entry.getKey()) != null) {
                    continue;
                }
                AggregatedCourse agg = entry.getValue();
                String name = agg.courseName == null ? "" : agg.courseName.replaceAll("\\s+", "");
                // 미이수 자기주도창의전공도 제거 (시간표 gy=1에 Ⅲ·Ⅳ가 붙는 경우)
                if (name.contains("자기주도창의전공")) {
                    iterator.remove();
                    continue;
                }
                String displayCategory = normalizeDisplayCategory(agg.category, agg.courseName);
                String bucket = classifyAbeekBucket(displayCategory, agg.courseName);
                if ("GENERAL".equals(bucket)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 기이수 과목을 입학 이후 정규학기 순번 칸에만 1회 배치.
     * 시간표 권장학년 칸에 남은 이수 과목/중복(1·2학기 동시 개설)을 제거한다.
     */
    private void placeCompletedCoursesByStanding(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Integer admissionYear
    ) {
        if (completion.isEmpty()) {
            return;
        }

        TranscriptStandingMapper standing =
                TranscriptStandingMapper.fromRows(completion.rows(), admissionYear);

        // 시간표 칸 제거 + 템플릿 보관 (동등 학수번호 포함)
        Map<String, AggregatedCourse> timetableTemplates = new LinkedHashMap<>();
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            var iterator = termMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AggregatedCourse> entry = iterator.next();
                if (completion.findByCourseCode(entry.getKey()) != null) {
                    AggregatedCourse template = entry.getValue();
                    for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(entry.getKey())) {
                        timetableTemplates.putIfAbsent(equiv, template);
                    }
                    iterator.remove();
                }
            }
        }

        Map<String, String> targetTermByNorm = new LinkedHashMap<>();
        Set<String> placedGroups = new HashSet<>();
        for (CompletionHit hit : completion.allHits()) {
            String termKey = standing.resolveTermKey(hit.year(), hit.semester());
            if (termKey == null || !byTermAndCode.containsKey(termKey)) {
                continue;
            }
            String norm = normalizeCourseCode(hit.courseCode());
            if (norm.isBlank()) {
                continue;
            }
            String group = RoadmapCourseCodeEquivalence.canonical(norm);
            if (!placedGroups.add(group)) {
                continue;
            }

            String displayCategory = normalizeDisplayCategory(hit.category(), hit.courseName());
            String bucket = classifyAbeekBucket(displayCategory, hit.courseName());
            // 기이수 중 전공·BSM이 아니면 교양필수로 정규화해 교양 행에 넣는다
            if (!"MAJOR".equals(bucket) && !"BSM".equals(bucket)) {
                displayCategory = "교양필수";
            }

            AggregatedCourse template = null;
            for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(norm)) {
                template = timetableTemplates.get(equiv);
                if (template != null) {
                    break;
                }
            }
            // 시간표(신 학수번호·과목명)가 있으면 그 칸에 이수 표시
            AggregatedCourse course = template != null
                    ? template.withCategory(displayCategory)
                    : AggregatedCourse.fromCompleted(
                            hit.courseCode(),
                            hit.courseName(),
                            displayCategory,
                            hit.credits()
                    );
            byTermAndCode.get(termKey).put(course.courseCode, course);

            for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(course.courseCode)) {
                targetTermByNorm.put(equiv, termKey);
            }
            for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(norm)) {
                targetTermByNorm.putIfAbsent(equiv, termKey);
            }
        }

        // 안전망: 동등 학수번호가 다른 학기 칸에 남아 있으면 제거
        for (Map.Entry<String, Map<String, AggregatedCourse>> termEntry : byTermAndCode.entrySet()) {
            String termKey = termEntry.getKey();
            var iterator = termEntry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AggregatedCourse> entry = iterator.next();
                String norm = normalizeCourseCode(entry.getKey());
                String target = targetTermByNorm.get(norm);
                if (target == null) {
                    for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(norm)) {
                        target = targetTermByNorm.get(equiv);
                        if (target != null) {
                            break;
                        }
                    }
                }
                if (target != null && !target.equals(termKey)) {
                    iterator.remove();
                }
            }
        }
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

    private RoadmapCourseDto toCourseDto(AggregatedCourse agg, CompletionIndex completion, String termKey) {
        CompletionHit hit = completion.findByCourseCode(agg.courseCode);
        String displayCategory = hit != null
                ? normalizeDisplayCategory(hit.category(), hit.courseName())
                : normalizeDisplayCategory(agg.category, agg.courseName);
        String bucket = classifyAbeekBucket(displayCategory, agg.courseName);
        // 기이수: 전공·BSM이 아니면 교양(GENERAL)으로 노출
        if (hit != null && !"MAJOR".equals(bucket) && !"BSM".equals(bucket)) {
            bucket = "GENERAL";
            if (!"교양필수".equals(displayCategory)) {
                displayCategory = "교양필수";
            }
        }
        return RoadmapCourseDto.builder()
                .courseCode(agg.courseCode)
                .courseName(agg.courseName)
                .category(displayCategory)
                .abeekBucket(bucket)
                .credits(agg.credits)
                .completed(hit != null)
                .takenYear(hit == null ? null : hit.year())
                .takenSemester(hit == null ? null : hit.semester())
                .standingTermKey(hit == null ? null : termKey)
                .grade(hit == null ? null : hit.grade())
                .sectionCount(agg.sectionCount)
                .build();
    }

    private List<RoadmapCourseDto> filterBucket(List<RoadmapCourseDto> courses, String bucket) {
        return courses.stream().filter(c -> bucket.equals(c.getAbeekBucket())).toList();
    }

    private List<RoadmapCourseDto> filterByDisplayCategory(List<RoadmapCourseDto> courses, String category) {
        return courses.stream().filter(c -> category.equals(c.getCategory())).toList();
    }

    /**
     * 기이수/시간표 구분을 로드맵 표시용으로 정규화.
     * 교필·공필·교선·균필 등 → 교양필수, 기필·학문기초 → 기초필수, 전공/자기주도창의전공 → 전공 계열 유지.
     */
    String normalizeDisplayCategory(String category, String courseName) {
        String cat = category == null ? "" : category.replaceAll("\\s+", "");
        String name = courseName == null ? "" : courseName.replaceAll("\\s+", "");

        if (cat.contains("교양필수") || cat.equals("교필") || cat.equals("공필") || cat.contains("중핵필수")
                || cat.equals("교선") || cat.contains("교양선택")
                || cat.equals("균필") || cat.contains("균형")
                || cat.equals("일선") || cat.contains("일반선택") || cat.contains("자유선택")) {
            return "교양필수";
        }
        if (cat.contains("기초필수") || cat.contains("학문기초") || cat.equals("기필")
                || isAcademicFoundationCourseName(name)) {
            return "기초필수";
        }
        if (cat.contains("전필") || cat.contains("전선") || cat.contains("전기")
                || cat.contains("전공필수") || cat.contains("전공선택") || cat.contains("전공기초")
                || cat.contains("전공") || name.contains("자기주도창의전공")) {
            if (cat.contains("전공기초") && isAcademicFoundationCourseName(name)) {
                return "기초필수";
            }
            if (cat.contains("전공필수") || cat.equals("전필")) {
                return "전공필수";
            }
            if (cat.contains("전공선택") || cat.equals("전선") || name.contains("자기주도창의전공")) {
                return "전공선택";
            }
            return cat.isBlank() ? "전공선택" : category;
        }
        if (cat.contains("교양") || cat.contains("중핵") || cat.contains("공통")) {
            return "교양필수";
        }
        return category;
    }

    private boolean isAcademicFoundationCourseName(String name) {
        String n = name == null ? "" : name.replaceAll("\\s+", "");
        return n.contains("미적분")
                || n.contains("공업수학")
                || n.contains("이산수학")
                || n.contains("선형대수")
                || (n.contains("확률") && n.contains("통계"))
                || n.contains("일반물리")
                || n.contains("물리학및실험")
                || n.contains("일반화학")
                || n.contains("일반생물");
    }

    /**
     * GENERAL=교양(공필/교선/균필 등), BSM=기초필수(학문기초), MAJOR=전공.
     * 전공·BSM이 아니면 GENERAL로 본다(기이수 교양 행 노출).
     */
    String classifyAbeekBucket(String category, String courseName) {
        String cat = category == null ? "" : category.replaceAll("\\s+", "");
        if ("기초필수".equals(cat) || cat.contains("학문기초") || cat.equals("기필")
                || isAcademicFoundationCourseName(courseName)) {
            return "BSM";
        }
        if (cat.contains("전공") || cat.contains("전필") || cat.contains("전선") || cat.contains("전기")
                || (courseName != null && courseName.replaceAll("\\s+", "").contains("자기주도창의전공"))) {
            return "MAJOR";
        }
        if ("교양필수".equals(cat) || cat.contains("교필") || cat.contains("공필")
                || cat.contains("전문교양") || cat.contains("중핵")
                || cat.equals("교선") || cat.contains("교양선택")
                || cat.equals("균필") || cat.contains("균형")
                || cat.equals("일선") || cat.contains("일반선택") || cat.contains("자유선택")
                || cat.contains("교양") || cat.contains("공통")) {
            return "GENERAL";
        }
        // 전공·BSM이 아니면 교양 행에 포함
        return "GENERAL";
    }

    private RoadmapSummaryDto buildSummary(List<RoadmapCourseDto> courses, boolean countBuckets) {
        int completed = 0;
        int general = 0;
        int bsm = 0;
        int major = 0;
        for (RoadmapCourseDto course : courses) {
            if (course.isCompleted()) {
                completed++;
            }
            if (countBuckets) {
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
        List<CompletedCourseUploadRowDto> kept = new ArrayList<>();
        for (CompletedCourseUploadRowDto row : rows) {
            if (!isPassed(row.grade())) {
                continue;
            }
            kept.add(row);
            String key = normalizeCourseCode(row.courseCode());
            if (key.isBlank()) {
                continue;
            }
            CompletionHit hit = new CompletionHit(
                    row.year(),
                    row.semester(),
                    row.grade(),
                    row.courseCode() == null ? "" : row.courseCode().trim(),
                    row.courseName(),
                    row.category(),
                    parseCredits(row.credit())
            );
            // 동등 학수번호(구·신)에도 같은 이수 hit를 걸어 시간표 신코드와 매칭
            for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(key)) {
                byCode.putIfAbsent(equiv, hit);
            }
        }
        return new CompletionIndex(byCode, List.copyOf(kept));
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
        return !(normalized.equals("F") || normalized.equals("NP") || normalized.equals("N")
                || normalized.equals("U") || normalized.equals("FA"));
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

        AggregatedCourse withCategory(String newCategory) {
            AggregatedCourse copy = new AggregatedCourse(courseCode, courseName, newCategory, credits);
            copy.sectionCount = Math.max(1, sectionCount);
            return copy;
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

    private record CompletionIndex(
            Map<String, CompletionHit> byNormalizedCode,
            List<CompletedCourseUploadRowDto> rows
    ) {
        static CompletionIndex empty() {
            return new CompletionIndex(Map.of(), List.of());
        }

        boolean isEmpty() {
            return byNormalizedCode == null || byNormalizedCode.isEmpty();
        }

        CompletionHit findByCourseCode(String courseCode) {
            String norm = normalizeCourseCode(courseCode);
            CompletionHit hit = byNormalizedCode.get(norm);
            if (hit != null) {
                return hit;
            }
            for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(norm)) {
                hit = byNormalizedCode.get(equiv);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }

        List<CompletionHit> allHits() {
            // 동등 코드로 중복 인덱싱된 hit는 한 번만
            return List.copyOf(new LinkedHashSet<>(byNormalizedCode.values()));
        }
    }
}
