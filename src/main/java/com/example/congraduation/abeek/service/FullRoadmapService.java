package com.example.congraduation.abeek.service;

import com.example.congraduation.abeek.domain.AbeekStudent;
import com.example.congraduation.abeek.domain.CourseMaster;
import com.example.congraduation.abeek.domain.CoursePrerequisite;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.StudentEnrollment;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.dto.FullRoadmapResponse;
import com.example.congraduation.abeek.dto.FullRoadmapResponse.RoadmapCourseDto;
import com.example.congraduation.abeek.dto.FullRoadmapResponse.RoadmapEdgeDto;
import com.example.congraduation.abeek.dto.FullRoadmapResponse.RoadmapSummaryDto;
import com.example.congraduation.abeek.dto.FullRoadmapResponse.TermRoadmapDto;
import com.example.congraduation.abeek.repository.AbeekStudentRepository;
import com.example.congraduation.abeek.repository.CoursePrerequisiteRepository;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FullRoadmapService {

    private static final List<String> TERM_KEYS = List.of(
            "1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2"
    );

    private final CurriculumCourseRepository curriculumCourseRepository;
    private final CoursePrerequisiteRepository coursePrerequisiteRepository;
    private final AbeekStudentRepository abeekStudentRepository;
    private final AbeekDepartmentCatalog departmentCatalog;

    @Transactional(readOnly = true)
    public FullRoadmapResponse getFullRoadmap(
            String departmentCode,
            int curriculumYear,
            String studentId
    ) {
        AbeekDepartmentCatalog.DepartmentInfo department = departmentCatalog.findByAbeekCode(departmentCode)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 학과코드: " + departmentCode));

        AbeekStudent student = null;
        if (studentId != null && !studentId.isBlank()) {
            student = abeekStudentRepository.findWithEnrollmentsByStudentId(studentId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("ABEEK 학생 없음: " + studentId));
        }

        List<CurriculumCourse> courses = curriculumCourseRepository
                .findAllWithMasterByDepartmentCodeAndYear(department.abeekCode(), curriculumYear);

        List<CoursePrerequisite> rawPrerequisites = coursePrerequisiteRepository
                .findByDepartmentCodeAndYear(department.abeekCode(), curriculumYear);

        Map<String, CurriculumCourse> byCode = new HashMap<>();
        Map<String, CurriculumCourse> byNormalizedName = new HashMap<>();
        for (CurriculumCourse course : courses) {
            CourseMaster master = course.getCourseMaster();
            byCode.put(master.getCourseCode(), course);
            putNameAliases(byNormalizedName, master.getName(), course);
        }

        List<RoadmapEdgeDto> edges = buildEdges(rawPrerequisites, byCode, byNormalizedName);
        Map<String, List<String>> prerequisitesByToCode = edges.stream()
                .collect(Collectors.groupingBy(
                        RoadmapEdgeDto::getToCourseCode,
                        Collectors.mapping(RoadmapEdgeDto::getFromCourseCode, Collectors.toList())
                ));

        CompletionIndex completion = buildCompletionIndex(student);

        Map<String, List<RoadmapCourseDto>> byTerm = new LinkedHashMap<>();
        for (String term : TERM_KEYS) {
            byTerm.put(term, new ArrayList<>());
        }
        List<RoadmapCourseDto> unscheduled = new ArrayList<>();

        for (CurriculumCourse course : courses) {
            RoadmapCourseDto dto = toCourseDto(course, completion, prerequisitesByToCode);
            String term = normalizeTerm(course.getRecommendedTerm());
            if (term != null && byTerm.containsKey(term)) {
                byTerm.get(term).add(dto);
            } else {
                unscheduled.add(dto);
            }
        }

        List<TermRoadmapDto> terms = new ArrayList<>();
        for (int i = 0; i < TERM_KEYS.size(); i++) {
            String termKey = TERM_KEYS.get(i);
            List<RoadmapCourseDto> termCourses = byTerm.get(termKey).stream()
                    .sorted(Comparator
                            .comparing((RoadmapCourseDto c) -> categoryOrder(c.getCategory()))
                            .thenComparing(RoadmapCourseDto::getCourseName))
                    .toList();

            Map<String, List<RoadmapCourseDto>> categories = new LinkedHashMap<>();
            categories.put("GENERAL", filterCategory(termCourses, CourseCategory.GENERAL));
            categories.put("BSM", filterCategory(termCourses, CourseCategory.BSM));
            categories.put("MAJOR", filterCategory(termCourses, CourseCategory.MAJOR));

            String[] parts = termKey.split("-");
            terms.add(TermRoadmapDto.builder()
                    .termKey(termKey)
                    .gradeYear(Integer.parseInt(parts[0]))
                    .semester(Integer.parseInt(parts[1]))
                    .termIndex(i + 1)
                    .categories(categories)
                    .build());
        }

        List<RoadmapCourseDto> all = new ArrayList<>();
        byTerm.values().forEach(all::addAll);
        all.addAll(unscheduled);

        return FullRoadmapResponse.builder()
                .departmentCode(department.abeekCode())
                .departmentName(department.name())
                .curriculumYear(curriculumYear)
                .studentId(student == null ? null : student.getStudentId())
                .studentName(student == null ? null : student.getName())
                .terms(terms)
                .unscheduledCourses(unscheduled)
                .edges(edges)
                .summary(buildSummary(all))
                .build();
    }

    @Transactional(readOnly = true)
    public FullRoadmapResponse getFullRoadmapByStudent(String studentId) {
        AbeekStudent student = abeekStudentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("ABEEK 학생 없음: " + studentId));
        return getFullRoadmap(student.getDepartmentCode(), student.getEntranceYear(), student.getStudentId());
    }

    private RoadmapCourseDto toCourseDto(
            CurriculumCourse course,
            CompletionIndex completion,
            Map<String, List<String>> prerequisitesByToCode
    ) {
        CourseMaster master = course.getCourseMaster();
        CourseCategory category = master.getCategory();
        CompletionHit hit = completion.find(master);

        return RoadmapCourseDto.builder()
                .abeekCourseCode(master.getCourseCode())
                .courseName(master.getName())
                .category(category)
                .categoryLabel(categoryLabel(category))
                .professionalLiberal(category == CourseCategory.GENERAL)
                .bsm(category == CourseCategory.BSM)
                .abeekMajor(category == CourseCategory.MAJOR)
                .role(course.getRole())
                .roleLabel(roleLabel(course.getRole()))
                .credits(course.getCredits())
                .designCredits(course.getDesignCredits())
                .hasDesignCredits(course.getDesignCredits() > 0)
                .designLevel(course.getDesignLevel())
                .recommendedTerm(course.getRecommendedTerm())
                .newlyIntroducedRequired(course.isNewlyIntroducedRequired())
                .completed(hit != null)
                .takenYear(hit == null ? null : hit.takenYear())
                .takenSemester(hit == null ? null : hit.takenSemester())
                .prerequisiteCourseCodes(prerequisitesByToCode.getOrDefault(master.getCourseCode(), List.of()))
                .build();
    }

    private List<RoadmapEdgeDto> buildEdges(
            List<CoursePrerequisite> rawPrerequisites,
            Map<String, CurriculumCourse> byCode,
            Map<String, CurriculumCourse> byNormalizedName
    ) {
        List<RoadmapEdgeDto> edges = new ArrayList<>();
        for (CoursePrerequisite prerequisite : rawPrerequisites) {
            CurriculumCourse from = resolveCourse(prerequisite.getFromCourseCode(), byCode, byNormalizedName);
            CurriculumCourse to = resolveCourse(prerequisite.getToCourseCode(), byCode, byNormalizedName);
            if (from == null || to == null) {
                continue;
            }
            String type = prerequisite.getType() == null ? "MANDATORY" : prerequisite.getType().toUpperCase(Locale.ROOT);
            if (type.contains("RECOMMENDED")) {
                type = "RECOMMENDED";
            } else {
                type = "MANDATORY";
            }
            edges.add(RoadmapEdgeDto.builder()
                    .fromCourseCode(from.getCourseMaster().getCourseCode())
                    .fromCourseName(from.getCourseMaster().getName())
                    .toCourseCode(to.getCourseMaster().getCourseCode())
                    .toCourseName(to.getCourseMaster().getName())
                    .edgeType(type)
                    .needsReview(prerequisite.isNeedsReview())
                    .fromTerm(normalizeTerm(from.getRecommendedTerm()))
                    .toTerm(normalizeTerm(to.getRecommendedTerm()))
                    .build());
        }

        // 중복 간선 제거
        Map<String, RoadmapEdgeDto> unique = new LinkedHashMap<>();
        for (RoadmapEdgeDto edge : edges) {
            String key = edge.getFromCourseCode() + "->" + edge.getToCourseCode() + ":" + edge.getEdgeType();
            unique.putIfAbsent(key, edge);
        }
        return List.copyOf(unique.values());
    }

    private CurriculumCourse resolveCourse(
            String raw,
            Map<String, CurriculumCourse> byCode,
            Map<String, CurriculumCourse> byNormalizedName
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        CurriculumCourse byExactCode = byCode.get(raw.trim());
        if (byExactCode != null) {
            return byExactCode;
        }
        return byNormalizedName.get(normalizeName(raw));
    }

    private RoadmapSummaryDto buildSummary(List<RoadmapCourseDto> courses) {
        int completed = 0;
        int general = 0;
        int bsm = 0;
        int major = 0;
        double totalDesign = 0;
        double completedDesign = 0;

        for (RoadmapCourseDto course : courses) {
            if (course.isProfessionalLiberal()) {
                general++;
            } else if (course.isBsm()) {
                bsm++;
            } else if (course.isAbeekMajor()) {
                major++;
            }
            totalDesign += course.getDesignCredits();
            if (course.isCompleted()) {
                completed++;
                completedDesign += course.getDesignCredits();
            }
        }

        return RoadmapSummaryDto.builder()
                .totalCourses(courses.size())
                .completedCourses(completed)
                .professionalLiberalCount(general)
                .bsmCount(bsm)
                .majorCount(major)
                .totalDesignCredits(totalDesign)
                .completedDesignCredits(completedDesign)
                .build();
    }

    private List<RoadmapCourseDto> filterCategory(List<RoadmapCourseDto> courses, CourseCategory category) {
        return courses.stream().filter(c -> c.getCategory() == category).toList();
    }

    private int categoryOrder(CourseCategory category) {
        return switch (category) {
            case GENERAL -> 0;
            case BSM -> 1;
            case MAJOR -> 2;
        };
    }

    private String categoryLabel(CourseCategory category) {
        return switch (category) {
            case GENERAL -> "전문교양";
            case BSM -> "BSM";
            case MAJOR -> "전공";
        };
    }

    private String roleLabel(CourseRole role) {
        return switch (role) {
            case REQUIRED -> "인증필수";
            case CERT_ELECTIVE -> "인증선택";
            case ELECTIVE -> "전공선택";
            case BSM_REQUIRED -> "BSM필수";
        };
    }

    private String normalizeTerm(String recommendedTerm) {
        if (recommendedTerm == null || recommendedTerm.isBlank()) {
            return null;
        }
        String value = recommendedTerm.trim().replace('－', '-');
        if (TERM_KEYS.contains(value)) {
            return value;
        }
        // "1학년 1학기" 같은 형태 보정
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() >= 2) {
            String candidate = digits.charAt(0) + "-" + digits.charAt(1);
            if (TERM_KEYS.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private CompletionIndex buildCompletionIndex(AbeekStudent student) {
        if (student == null || student.getEnrollments() == null) {
            return CompletionIndex.empty();
        }

        Map<String, CompletionHit> byCode = new HashMap<>();
        Map<String, CompletionHit> byEquivalence = new HashMap<>();
        Map<String, CompletionHit> byNormalizedName = new HashMap<>();

        for (StudentEnrollment enrollment : student.getEnrollments()) {
            if (!enrollment.isPassed() || enrollment.getCourseMaster() == null) {
                continue;
            }
            CourseMaster master = enrollment.getCourseMaster();
            CompletionHit hit = new CompletionHit(enrollment.getTakenYear(), enrollment.getTakenSemester());
            byCode.put(master.getCourseCode(), hit);
            if (master.getEquivalenceGroup() != null && !master.getEquivalenceGroup().isBlank()) {
                byEquivalence.putIfAbsent(master.getEquivalenceGroup(), hit);
            }
            putNameAliases(byNormalizedName, master.getName(), hit);
        }
        return new CompletionIndex(byCode, byEquivalence, byNormalizedName);
    }

    private <T> void putNameAliases(Map<String, T> index, String rawName, T value) {
        String normalized = normalizeName(rawName);
        if (!normalized.isBlank()) {
            index.putIfAbsent(normalized, value);
        }
        if (rawName == null) {
            return;
        }
        String withoutParen = normalizeName(rawName.replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            index.putIfAbsent(withoutParen, value);
        }
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("\\s+", "")
                .replace('：', ':')
                .replace('（', '(')
                .replace('）', ')')
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private record CompletionHit(int takenYear, int takenSemester) {
    }

    private record CompletionIndex(
            Map<String, CompletionHit> byCode,
            Map<String, CompletionHit> byEquivalence,
            Map<String, CompletionHit> byNormalizedName
    ) {
        static CompletionIndex empty() {
            return new CompletionIndex(Map.of(), Map.of(), Map.of());
        }

        CompletionHit find(CourseMaster master) {
            CompletionHit byExact = byCode.get(master.getCourseCode());
            if (byExact != null) {
                return byExact;
            }
            if (master.getEquivalenceGroup() != null) {
                CompletionHit byGroup = byEquivalence.get(master.getEquivalenceGroup());
                if (byGroup != null) {
                    return byGroup;
                }
            }
            return byNormalizedName.get(master.getName() == null
                    ? ""
                    : master.getName().replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
        }
    }
}
