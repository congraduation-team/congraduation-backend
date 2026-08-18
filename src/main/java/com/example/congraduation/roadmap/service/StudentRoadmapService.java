package com.example.congraduation.roadmap.service;

import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.abeek.service.AbeekDepartmentCatalog;
import com.example.congraduation.abeek.service.SejongAbeekCourseCodeCatalog;
import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.exception.TranscriptNotFoundException;
import com.example.congraduation.roadmap.dto.RoadmapDepartmentDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.RoadmapCourseDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.RoadmapSummaryDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.SourceTermDto;
import com.example.congraduation.roadmap.dto.StudentRoadmapResponse.TermRoadmapDto;
import com.example.congraduation.service.graduation.AcademicFoundationCoursePolicyService;
import com.example.congraduation.service.graduation.AcademicFoundationCoursePolicyService.RequiredFoundationSlot;
import com.example.congraduation.service.graduation.BalancedLiberalCoursePolicyService;
import com.example.congraduation.service.transcript.TranscriptStandingMapper;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentRoadmapService {

    private static final List<String> TERM_KEYS = List.of(
            "1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2"
    );

    private final TimetableCatalog timetableCatalog;
    private final AbeekDepartmentCatalog departmentCatalog;
    private final TranscriptStorageService transcriptStorageService;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final SejongAbeekCourseCodeCatalog sejongAbeekCourseCodeCatalog;
    private final AcademicFoundationCoursePolicyService academicFoundationCoursePolicyService;
    private final BalancedLiberalCoursePolicyService balancedLiberalCoursePolicyService;

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
                student.getAdmissionYear(),
                true
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
        boolean attachStudentCoreCompletion = true;
        if (studentDbId != null) {
            student = transcriptStorageService.getStudentOrThrow(studentDbId);
            completion = buildCompletionIndex(studentDbId);
            admissionYear = student.getAdmissionYear();
            attachStudentCoreCompletion = isSameDepartment(resolvedName, student.getMajor());
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
                admissionYear,
                attachStudentCoreCompletion
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
            Integer admissionYear,
            boolean attachStudentCoreCompletion
    ) {
        int foundationYear = admissionYear != null ? admissionYear : 2026;
        Set<String> departmentFoundationNames = academicFoundationCoursePolicyService == null
                ? Set.of()
                : academicFoundationCoursePolicyService.requiredCourseNames(departmentName, foundationYear);

        List<TimetableTermData> sourceTerms = resolveSourceTerms();
        Map<String, Map<String, AggregatedCourse>> byTermAndCode = new LinkedHashMap<>();
        for (String termKey : TERM_KEYS) {
            byTermAndCode.put(termKey, new LinkedHashMap<>());
        }

        List<SourceTermDto> sourceDtos = new ArrayList<>();
        for (TimetableTermData term : sourceTerms) {
            int used = 0;
            for (TimetableOffering offering : term.offerings()) {
                if (!shouldIncludeOffering(
                        offering,
                        openingNames,
                        abeekTarget,
                        departmentName,
                        foundationYear
                )) {
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
        placeCompletedCoursesByStanding(
                byTermAndCode,
                completion,
                admissionYear,
                attachStudentCoreCompletion
        );

        // 학생 로드맵: 교양(GENERAL)은 기이수만. 입학연도 공필·학문기초 필수는 미이수여도 유지.
        if (studentDbId != null) {
            stripIncompleteGeneralCourses(byTermAndCode, completion, admissionYear);
        }
        // 공학인증 학과: 전교 공통 기초필수 중 소속 BSM이 아닌 미이수 슬롯 제거
        // (입학연도 없으면 최신 커리큘럼 기준으로 걸러 음악과 등이 아닌 컴공 학과 조회에도 화학·생물이 안 남게 한다)
        if (abeekTarget && abeekCode != null) {
            int curriculumYear = admissionYear != null ? admissionYear : 2026;
            stripIncompleteNonCurriculumBsm(
                    byTermAndCode,
                    completion,
                    loadAbeekBsmAllowlist(abeekCode, curriculumYear)
            );
        } else {
            stripIncompleteUnlistedFoundation(byTermAndCode, completion, departmentFoundationNames);
        }

        // 시간표에 없거나 교양 미이수로 걸러진 공통교양·학문기초 필수 과목은 빈 칸으로 다시 넣는다.
        injectRequiredIncompleteSlots(
                byTermAndCode,
                departmentName,
                foundationYear,
                attachStudentCoreCompletion ? completion : CompletionIndex.empty(),
                sourceTerms
        );

        // 동일 학수번호가 1·2학기 시간표에 모두 있으면, 커리큘럼 recommendedTerm이 있을 때만 한 칸으로 합친다.
        // (선호 없을 때 이른 칸(*-1)을 강제하면 가을 전용 과목이 3-1/4-1로 밀리고 3-2/4-2가 비게 됨)
        Map<String, String> preferredTermByCode = Map.of();
        if (abeekTarget && abeekCode != null && admissionYear != null) {
            preferredTermByCode = loadPreferredTermBySejongCode(abeekCode, admissionYear);
        }
        dedupeIncompleteCourseCodesAcrossTerms(byTermAndCode, completion, preferredTermByCode);

        List<TermRoadmapDto> terms = new ArrayList<>();
        List<RoadmapCourseDto> allCourses = new ArrayList<>();
        for (int i = 0; i < TERM_KEYS.size(); i++) {
            String termKey = TERM_KEYS.get(i);
            String[] parts = termKey.split("-");
            List<RoadmapCourseDto> courses = byTermAndCode.get(termKey).values().stream()
                    .sorted(Comparator.comparing(AggregatedCourse::courseName, Comparator.nullsLast(String::compareTo)))
                    .map(agg -> toCourseDto(
                            agg,
                            completion,
                            termKey,
                            abeekTarget,
                            departmentFoundationNames,
                            attachStudentCoreCompletion
                    ))
                    .toList();
            allCourses.addAll(courses);

            Map<String, List<RoadmapCourseDto>> categories = new LinkedHashMap<>();
            // 교양 행: 전공·BSM이 아닌 기이수 (공필/교선/균필 등)
            List<RoadmapCourseDto> general = filterBucket(courses, "GENERAL");
            if (studentDbId != null) {
                general = general.stream()
                        .filter(course -> course.isCompleted() || "교양필수".equals(course.getCategory()))
                        .toList();
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
        TimetableTermData fall = latestNonEmptyTermForSemester(2)
                .orElseThrow(() -> new IllegalArgumentException("2학기 강의시간표 데이터가 없습니다."));
        TimetableTermData spring = latestNonEmptyTermForSemester(1)
                .orElseThrow(() -> new IllegalArgumentException("1학기 강의시간표 데이터가 없습니다."));

        // 봄 슬롯이 가을 시간표 복사본이면(잘못된 업로드) 이전 연도 봄을 사용
        if (TimetableCatalog.isNearDuplicateTerm(spring, fall)) {
            Optional<TimetableTermData> olderSpring = findOlderDistinctSpring(spring, fall);
            if (olderSpring.isPresent()) {
                log.warn(
                        "Latest spring {}-{} duplicates fall {}-{}; using older spring {}-{}",
                        spring.termYear(), spring.semester(),
                        fall.termYear(), fall.semester(),
                        olderSpring.get().termYear(), olderSpring.get().semester()
                );
                spring = olderSpring.get();
            } else {
                log.warn(
                        "Latest spring {}-{} duplicates fall {}-{} and no older distinct spring exists",
                        spring.termYear(), spring.semester(),
                        fall.termYear(), fall.semester()
                );
            }
        }
        // 1학기·2학기 모두 사용해 1-1~4-2 채움
        return List.of(spring, fall);
    }

    /** offerings가 비어 있지 않은 최신 학기 시간표 */
    private Optional<TimetableTermData> latestNonEmptyTermForSemester(int semester) {
        return timetableCatalog.availableTerms().stream()
                .filter(term -> term.semester() == semester)
                .filter(term -> term.offerings() != null && !term.offerings().isEmpty())
                .findFirst();
    }

    private Optional<TimetableTermData> findOlderDistinctSpring(
            TimetableTermData badSpring,
            TimetableTermData fall
    ) {
        return timetableCatalog.availableTerms().stream()
                .filter(term -> term.semester() == 1)
                .filter(term -> term.offerings() != null && !term.offerings().isEmpty())
                .filter(term -> term.termYear() != badSpring.termYear()
                        || term.semester() != badSpring.semester())
                .filter(term -> !TimetableCatalog.isNearDuplicateTerm(term, fall))
                .findFirst();
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

    private boolean shouldIncludeOffering(
            TimetableOffering offering,
            Set<String> openingNames,
            boolean abeekTarget,
            String departmentName,
            int foundationYear
    ) {
        if (matchesOpeningDepartment(offering, openingNames)) {
            return true;
        }
        if (!isCommonRequiredOffering(offering)) {
            return false;
        }
        // 공학인증: 전교 기초필수를 넣은 뒤 소속 BSM allowlist로 거른다.
        if (abeekTarget) {
            return true;
        }
        // 비공학인증: 수강편람 학과별 학문기초(음악과=코딩·AI빅데이터 등)만 넣는다.
        return academicFoundationCoursePolicyService != null
                && academicFoundationCoursePolicyService.matchesRequiredCourse(
                offering.courseName(),
                departmentName,
                foundationYear
        );
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
     * English Listening/Reading 등 입학연도 공필이 아닌 교양은 미이수로 남기지 않는다.
     * 입학연도 공통교양 필수(공필)는 들어야 하므로 유지한다.
     */
    private void stripIncompleteGeneralCourses(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Integer admissionYear
    ) {
        Set<String> requiredCommonNames = balancedLiberalCoursePolicyService == null
                ? Set.of()
                : balancedLiberalCoursePolicyService.requiredCommonLiberalNormalizedNames(admissionYear);
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
                if (!"GENERAL".equals(bucket)) {
                    continue;
                }
                if (requiredCommonNames.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                iterator.remove();
            }
        }
    }

    /**
     * 공학인증 학과 로드맵에서, 전교 공통 기초필수·학문기초로 들어온 미이수 과목 중
     * 해당 입학연도 ABEEK BSM 커리큘럼에 없는 것(예: 컴공의 미적분학2·일반화학1·일반생물학)을 제거한다.
     */
    private void stripIncompleteNonCurriculumBsm(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            AbeekBsmAllowlist allowlist
    ) {
        if (allowlist.isEmpty()) {
            return;
        }
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            var iterator = termMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AggregatedCourse> entry = iterator.next();
                if (completion.findByCourseCode(entry.getKey()) != null) {
                    continue;
                }
                AggregatedCourse agg = entry.getValue();
                String displayCategory = normalizeDisplayCategory(agg.category, agg.courseName);
                String bucket = classifyAbeekBucket(displayCategory, agg.courseName);
                if (!"BSM".equals(bucket) && !"기초필수".equals(displayCategory)) {
                    continue;
                }
                if (!allowlist.matches(agg.courseCode, agg.courseName)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 비공학인증 학과: 수강편람 학문기초에 없는 미이수 기초필수(일반물리 등)를 제거한다.
     */
    private void stripIncompleteUnlistedFoundation(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Set<String> allowedNames
    ) {
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            var iterator = termMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AggregatedCourse> entry = iterator.next();
                if (completion.findByCourseCode(entry.getKey()) != null) {
                    continue;
                }
                AggregatedCourse agg = entry.getValue();
                String displayCategory = normalizeDisplayCategory(agg.category, agg.courseName);
                String bucket = classifyAbeekBucket(displayCategory, agg.courseName);
                if (!"BSM".equals(bucket) && !"기초필수".equals(displayCategory)) {
                    continue;
                }
                String normalized = agg.courseName == null
                        ? ""
                        : agg.courseName.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
                if (allowedNames == null || !allowedNames.contains(normalized)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 수강편람 기준 미이수 공통교양·학문기초 필수를 권장학기 칸에 넣는다.
     * 시간표 개설학과가 대양휴머니티칼리지인 코딩·대학영어 등이 학과 로드맵에서 빠지는 것을 막는다.
     */
    private void injectRequiredIncompleteSlots(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            String departmentName,
            int foundationYear,
            CompletionIndex completion,
            List<TimetableTermData> sourceTerms
    ) {
        Map<String, TimetableOffering> offeringByName = indexOfferingsByNormalizedName(sourceTerms);
        Set<String> existingNames = collectNormalizedCourseNames(byTermAndCode);
        Set<String> existingCodes = collectNormalizedCourseCodes(byTermAndCode);

        if (academicFoundationCoursePolicyService != null) {
            List<RequiredFoundationSlot> remaining = academicFoundationCoursePolicyService.remainingRequiredSlots(
                    departmentName,
                    foundationYear,
                    completion.rows()
            );
            for (RequiredFoundationSlot slot : remaining) {
                if (roadmapAlreadyHasAliases(existingNames, slot.equivalentNames(), slot.courseName())) {
                    continue;
                }
                TimetableOffering hint = findOfferingHint(offeringByName, slot.equivalentNames(), slot.courseName());
                String code = resolveInjectedCourseCode(hint, "REQ-AF-" + normalizeCourseName(slot.courseName()));
                if (code.isBlank() || existingCodes.contains(normalizeCourseCode(code))) {
                    continue;
                }
                String termKey = resolveInjectedTermKey(slot.recommendedTerm());
                putInjectedCourse(
                        byTermAndCode,
                        termKey,
                        code,
                        resolveInjectedCourseName(hint, slot.courseName()),
                        "기초필수",
                        hint != null ? hint.credits() : parseCredits(slot.credit())
                );
                existingCodes.add(normalizeCourseCode(code));
                existingNames.add(normalizeCourseName(slot.courseName()).toLowerCase(Locale.ROOT));
                for (String alias : slot.equivalentNames()) {
                    existingNames.add(normalizeCourseName(alias).toLowerCase(Locale.ROOT));
                }
            }
        }

        if (balancedLiberalCoursePolicyService == null) {
            return;
        }
        for (CategoryCourseDto required : balancedLiberalCoursePolicyService.requiredCommonLiberalCourses(foundationYear)) {
            if (balancedLiberalCoursePolicyService.hasCompletedEquivalent(required, completion.rows())) {
                continue;
            }
            Set<String> aliases = new LinkedHashSet<>(
                    balancedLiberalCoursePolicyService.commonLiberalEquivalentNames(required.courseCode())
            );
            aliases.add(required.courseName());
            if (roadmapAlreadyHasAliases(existingNames, aliases, required.courseName())) {
                continue;
            }
            TimetableOffering hint = findOfferingHint(offeringByName, aliases, required.courseName());
            String code = resolveInjectedCourseCode(hint, required.courseCode());
            if (code == null || code.isBlank() || existingCodes.contains(normalizeCourseCode(code))) {
                continue;
            }
            String termKey = resolveInjectedTermKey(
                    balancedLiberalCoursePolicyService.recommendedTermForCommonLiberal(required.courseCode())
            );
            putInjectedCourse(
                    byTermAndCode,
                    termKey,
                    code,
                    resolveInjectedCourseName(hint, required.courseName()),
                    "교양필수",
                    hint != null ? hint.credits() : parseCredits(required.credit())
            );
            existingCodes.add(normalizeCourseCode(code));
            existingNames.add(normalizeCourseName(required.courseName()).toLowerCase(Locale.ROOT));
            for (String alias : aliases) {
                existingNames.add(normalizeCourseName(alias).toLowerCase(Locale.ROOT));
            }
        }
    }

    private Map<String, TimetableOffering> indexOfferingsByNormalizedName(List<TimetableTermData> sourceTerms) {
        Map<String, TimetableOffering> byName = new LinkedHashMap<>();
        if (sourceTerms == null) {
            return byName;
        }
        for (TimetableTermData term : sourceTerms) {
            if (term.offerings() == null) {
                continue;
            }
            for (TimetableOffering offering : term.offerings()) {
                String key = normalizeCourseName(offering.courseName()).toLowerCase(Locale.ROOT);
                if (!key.isBlank()) {
                    byName.putIfAbsent(key, offering);
                }
            }
        }
        return byName;
    }

    private Set<String> collectNormalizedCourseNames(Map<String, Map<String, AggregatedCourse>> byTermAndCode) {
        Set<String> names = new HashSet<>();
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            for (AggregatedCourse course : termMap.values()) {
                String name = normalizeCourseName(course.courseName()).toLowerCase(Locale.ROOT);
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private Set<String> collectNormalizedCourseCodes(Map<String, Map<String, AggregatedCourse>> byTermAndCode) {
        Set<String> codes = new HashSet<>();
        for (Map<String, AggregatedCourse> termMap : byTermAndCode.values()) {
            for (String code : termMap.keySet()) {
                String normalized = normalizeCourseCode(code);
                if (!normalized.isBlank()) {
                    codes.add(normalized);
                }
            }
        }
        return codes;
    }

    private boolean roadmapAlreadyHasAliases(Set<String> existingNames, Set<String> aliases, String courseName) {
        if (existingNames.contains(normalizeCourseName(courseName).toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (aliases == null) {
            return false;
        }
        for (String alias : aliases) {
            if (existingNames.contains(normalizeCourseName(alias).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private TimetableOffering findOfferingHint(
            Map<String, TimetableOffering> offeringByName,
            Set<String> aliases,
            String courseName
    ) {
        TimetableOffering hit = offeringByName.get(normalizeCourseName(courseName).toLowerCase(Locale.ROOT));
        if (hit != null) {
            return hit;
        }
        if (aliases == null) {
            return null;
        }
        for (String alias : aliases) {
            hit = offeringByName.get(normalizeCourseName(alias).toLowerCase(Locale.ROOT));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private String resolveInjectedCourseCode(TimetableOffering hint, String fallback) {
        if (hint != null && hint.courseCode() != null && !hint.courseCode().isBlank()) {
            return hint.courseCode().trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private String resolveInjectedCourseName(TimetableOffering hint, String fallback) {
        if (hint != null && hint.courseName() != null && !hint.courseName().isBlank()) {
            return hint.courseName().trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private String resolveInjectedTermKey(String recommendedTerm) {
        if (recommendedTerm != null && TERM_KEYS.contains(recommendedTerm.trim())) {
            return recommendedTerm.trim();
        }
        return "1-1";
    }

    private void putInjectedCourse(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            String termKey,
            String courseCode,
            String courseName,
            String category,
            Double credits
    ) {
        Map<String, AggregatedCourse> termMap = byTermAndCode.get(termKey);
        if (termMap == null) {
            return;
        }
        termMap.putIfAbsent(courseCode, AggregatedCourse.fromCompleted(courseCode, courseName, category, credits));
    }

    /**
     * 미이수 과목이 여러 학기 칸에 같은 학수번호로 중복되면
     * (예: Capstone이 1·2학기 시간표에 모두 gy=4) 한 칸만 남긴다.
     * 커리큘럼 recommendedTerm이 있으면 그 칸을 우선하고, 없으면 TERM_KEYS 앞쪽을 유지한다.
     */
    private void dedupeIncompleteCourseCodesAcrossTerms(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Map<String, String> preferredTermByCode
    ) {
        Map<String, List<String>> termsByCode = new LinkedHashMap<>();
        for (String termKey : TERM_KEYS) {
            Map<String, AggregatedCourse> termMap = byTermAndCode.get(termKey);
            if (termMap == null || termMap.isEmpty()) {
                continue;
            }
            for (String code : termMap.keySet()) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                if (completion.findByCourseCode(code) != null) {
                    continue;
                }
                String normalized = code.trim().toUpperCase(Locale.ROOT);
                termsByCode.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(termKey);
            }
        }

        for (Map.Entry<String, List<String>> entry : termsByCode.entrySet()) {
            List<String> terms = entry.getValue();
            if (terms.size() <= 1) {
                continue;
            }
            String code = entry.getKey();
            String preferred = preferredTermByCode.get(code);
            // 커리큘럼 권장학기가 있을 때만 한 칸으로 합침. 없으면 개설 학기(1·2) 모두 유지.
            if (preferred == null || !terms.contains(preferred)) {
                continue;
            }
            for (String termKey : terms) {
                if (!termKey.equals(preferred)) {
                    byTermAndCode.get(termKey).remove(code);
                    // 원본 키가 대소문자/공백 다를 수 있어 스캔 삭제
                    byTermAndCode.get(termKey).entrySet()
                            .removeIf(e -> e.getKey() != null
                                    && e.getKey().trim().equalsIgnoreCase(code));
                }
            }
        }
    }

    /** ABEEK 커리큘럼 recommendedTerm → 세종 학수번호 기준 선호 학기 칸. */
    private Map<String, String> loadPreferredTermBySejongCode(String departmentCode, int curriculumYear) {
        Map<String, String> preferred = new HashMap<>();
        List<CurriculumCourse> courses =
                curriculumCourseRepository.findByDepartmentCodeAndCurriculumYear(departmentCode, curriculumYear);
        for (CurriculumCourse course : courses) {
            if (course.getCourseMaster() == null) {
                continue;
            }
            String term = normalizeRecommendedTerm(course.getRecommendedTerm());
            if (term == null) {
                continue;
            }
            String abeekCode = course.getCourseMaster().getCourseCode();
            sejongAbeekCourseCodeCatalog.findSejongCourseCode(abeekCode).ifPresent(sejong ->
                    preferred.putIfAbsent(sejong.trim().toUpperCase(Locale.ROOT), term)
            );
        }
        return preferred;
    }

    private static String normalizeRecommendedTerm(String recommendedTerm) {
        if (recommendedTerm == null || recommendedTerm.isBlank()) {
            return null;
        }
        String value = recommendedTerm.trim().replace('－', '-');
        if (TERM_KEYS.contains(value)) {
            return value;
        }
        return null;
    }

    static String normalizeCourseName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.replaceAll("\\s+", "");
    }

    /**
     * 기이수 과목을 입학 이후 정규학기 순번 칸에만 1회 배치.
     * 시간표 권장학년 칸에 남은 이수 과목/중복(1·2학기 동시 개설)을 제거한다.
     * 타학과 조회 시 학문기초·공통교양·전공 기이수는 붙이지 않는다.
     */
    private void placeCompletedCoursesByStanding(
            Map<String, Map<String, AggregatedCourse>> byTermAndCode,
            CompletionIndex completion,
            Integer admissionYear,
            boolean attachStudentCoreCompletion
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
                if (completion.findByCourseCode(entry.getKey()) == null) {
                    continue;
                }
                AggregatedCourse template = entry.getValue();
                if (!attachStudentCoreCompletion && isStudentCoreCompletedCategory(template.category, template.courseName)) {
                    continue;
                }
                for (String equiv : RoadmapCourseCodeEquivalence.equivalentsIncludingSelf(entry.getKey())) {
                    timetableTemplates.putIfAbsent(equiv, template);
                }
                iterator.remove();
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
            if (!attachStudentCoreCompletion && isStudentCoreCompletedCategory(displayCategory, hit.courseName())) {
                continue;
            }
            // 전공·BSM이 아닌 기이수는 교양 행(GENERAL)에 두되, 공필/교선/균필 구분은 유지한다.

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

    private RoadmapCourseDto toCourseDto(
            AggregatedCourse agg,
            CompletionIndex completion,
            String termKey,
            boolean abeekTarget,
            Set<String> departmentFoundationNames,
            boolean attachStudentCoreCompletion
    ) {
        CompletionHit hit = completion.findByCourseCode(agg.courseCode);
        String displayCategory = hit != null
                ? normalizeDisplayCategory(hit.category(), hit.courseName())
                : normalizeDisplayCategory(agg.category, agg.courseName);
        if (!attachStudentCoreCompletion && hit != null
                && isStudentCoreCompletedCategory(displayCategory, agg.courseName)) {
            hit = null;
            displayCategory = normalizeDisplayCategory(agg.category, agg.courseName);
        }
        String bucket = classifyAbeekBucket(displayCategory, agg.courseName);
        // 선택한 학과의 학문기초가 아닌 기이수(예: 컴공 기초미적분학을 음악과 로드맵에서 볼 때)는 학문기초 행에 두지 않는다.
        if (!abeekTarget && departmentFoundationNames != null && !departmentFoundationNames.isEmpty()) {
            String normalizedName = agg.courseName == null
                    ? ""
                    : agg.courseName.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
            if (!normalizedName.isBlank()
                    && ("기초필수".equals(displayCategory) || "BSM".equals(bucket))
                    && !departmentFoundationNames.contains(normalizedName)) {
                displayCategory = "교양선택";
                bucket = "GENERAL";
            }
        }
        // 기이수: 전공·BSM이 아니면 교양(GENERAL)으로 노출. 표시 이수구분은 공필/교선/균필을 유지한다.
        if (hit != null && !"MAJOR".equals(bucket) && !"BSM".equals(bucket)) {
            bucket = "GENERAL";
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

    private boolean isSameDepartment(String selected, String studentMajor) {
        String a = selected == null ? "" : selected.replaceAll("\\s+", "");
        String b = studentMajor == null ? "" : studentMajor.replaceAll("\\s+", "");
        if (a.isBlank() || b.isBlank()) {
            return true;
        }
        if (a.equals(b) || a.contains(b) || b.contains(a)) {
            return true;
        }
        Optional<AbeekDepartmentCatalog.DepartmentInfo> selectedAbeek =
                departmentCatalog.findByDepartmentName(selected);
        Optional<AbeekDepartmentCatalog.DepartmentInfo> studentAbeek =
                departmentCatalog.findByDepartmentName(studentMajor);
        return selectedAbeek.isPresent()
                && studentAbeek.isPresent()
                && selectedAbeek.get().abeekCode().equals(studentAbeek.get().abeekCode());
    }

    private boolean isStudentCoreCompletedCategory(String category, String courseName) {
        String display = normalizeDisplayCategory(category, courseName);
        String bucket = classifyAbeekBucket(display, courseName);
        return "기초필수".equals(display)
                || "교양필수".equals(display)
                || "BSM".equals(bucket)
                || "MAJOR".equals(bucket);
    }

    private List<RoadmapCourseDto> filterBucket(List<RoadmapCourseDto> courses, String bucket) {
        return courses.stream().filter(c -> bucket.equals(c.getAbeekBucket())).toList();
    }

    private List<RoadmapCourseDto> filterByDisplayCategory(List<RoadmapCourseDto> courses, String category) {
        return courses.stream().filter(c -> category.equals(c.getCategory())).toList();
    }

    /**
     * 기이수/시간표 구분을 로드맵 표시용으로 정규화.
     * 수강편람: 공통교양필수 / 균형교양필수 / 교양선택 / 학문기초교양필수는 서로 다른 구분이다.
     */
    String normalizeDisplayCategory(String category, String courseName) {
        String cat = category == null ? "" : category.replaceAll("\\s+", "");
        String name = courseName == null ? "" : courseName.replaceAll("\\s+", "");

        if (cat.contains("학문기초") || cat.contains("기초필수") || cat.equals("기필")) {
            return "기초필수";
        }
        if (cat.equals("균필") || cat.contains("균형")) {
            return "균형교양";
        }
        if (cat.startsWith("교선") || cat.contains("교양선택")) {
            return "교양선택";
        }
        if (cat.equals("일선") || cat.contains("일반선택") || cat.contains("자유선택")) {
            return "일반선택";
        }
        if (cat.contains("공통교양") || cat.equals("공필") || cat.equals("교필")
                || cat.contains("교양필수") || cat.contains("중핵필수")
                || cat.contains("대학필수") || cat.contains("필수교양")
                || (cat.contains("공통") && cat.contains("필수"))) {
            return "교양필수";
        }
        if (isAcademicFoundationCourseName(name)) {
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
            if (cat.contains("선택") || cat.equals("교양")) {
                return "교양선택";
            }
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
                || n.contains("일반생물")
                || n.contains("컴퓨터사고")
                || n.contains("전산개론")
                || n.contains("인공지능과빅데이터")
                || n.contains("SW기초코딩")
                || n.contains("고급프로그래밍활용");
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
                || cat.startsWith("교선") || cat.contains("교양선택")
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

    private AbeekBsmAllowlist loadAbeekBsmAllowlist(String departmentCode, int curriculumYear) {
        Set<String> names = new HashSet<>();
        Set<String> codes = new HashSet<>();
        List<CurriculumCourse> courses =
                curriculumCourseRepository.findByDepartmentCodeAndCurriculumYear(departmentCode, curriculumYear);
        for (CurriculumCourse course : courses) {
            if (course.getCourseMaster() == null
                    || course.getCourseMaster().getCategory() != CourseCategory.BSM) {
                continue;
            }
            String name = normalizeCourseName(course.getCourseMaster().getName());
            if (!name.isBlank()) {
                names.add(name);
                addBsmNameAliases(names, name);
            }
            String code = course.getCourseMaster().getCourseCode();
            if (code != null && !code.isBlank()) {
                String upper = code.trim().toUpperCase(Locale.ROOT);
                codes.add(upper);
                addBsmCodeAliases(codes, upper);
            }
        }
        return new AbeekBsmAllowlist(names, codes, sejongAbeekCourseCodeCatalog);
    }

    /**
     * Incomplete 슬롯용 표기 alias.
     * 구·신 과목명(확률/선형대수)만 허용한다.
     * 기초미적분학↔미적분학1, 일반물리학및실험1↔일반물리학1 은 다른 과목(학수번호도 다름)이므로
     * incomplete allowlist에 넣지 않는다. (CSE 2021에 전교 공통 미적분학1·일반물리학1이 남는 문제 방지)
     */
    private static void addBsmNameAliases(Set<String> names, String name) {
        if (name.equals("선형대수")) {
            names.add("선형대수및프로그래밍");
        } else if (name.equals("선형대수및프로그래밍")) {
            names.add("선형대수");
        } else if (name.equals("확률및통계")) {
            names.add("확률통계및프로그래밍");
        } else if (name.equals("확률통계및프로그래밍")) {
            names.add("확률및통계");
        }
    }

    private static void addBsmCodeAliases(Set<String> codes, String code) {
        if (code.equals("BSM_PROB") || code.equals("BSM_PROB_PROG")) {
            codes.add("BSM_PROB");
            codes.add("BSM_PROB_PROG");
        } else if (code.equals("BSM_LINEAR") || code.equals("BSM_LINEAR_PROG")) {
            codes.add("BSM_LINEAR");
            codes.add("BSM_LINEAR_PROG");
        }
    }

    /**
     * ABEEK BSM 허용 과목(이름·내부코드). 미적분학1 ≠ 미적분학2 처럼 번호가 다른 과목은 별개로 취급.
     */
    static final class AbeekBsmAllowlist {
        private final Set<String> names;
        private final Set<String> codes;
        private final SejongAbeekCourseCodeCatalog catalog;

        AbeekBsmAllowlist(Set<String> names, Set<String> codes, SejongAbeekCourseCodeCatalog catalog) {
            this.names = names;
            this.codes = codes;
            this.catalog = catalog;
        }

        boolean isEmpty() {
            return names.isEmpty() && codes.isEmpty();
        }

        boolean matches(String sejongCourseCode, String courseName) {
            String normName = normalizeCourseName(courseName);
            if (!normName.isBlank() && names.contains(normName)) {
                return true;
            }
            if (catalog != null) {
                Optional<String> abeek = catalog.findAbeekCourseCode(sejongCourseCode);
                if (abeek.isPresent() && codes.contains(abeek.get().toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
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
