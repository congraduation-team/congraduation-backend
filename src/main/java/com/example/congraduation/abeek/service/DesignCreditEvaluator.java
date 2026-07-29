package com.example.congraduation.abeek.service;

import org.springframework.stereotype.Service;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.StudentEnrollment;
import com.example.congraduation.abeek.domain.enums.DesignLevel;
import com.example.congraduation.abeek.dto.DesignEvaluationResult;
import com.example.congraduation.abeek.dto.DesignCourseResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 설계학점 인정 규칙:
 * <ul>
 *   <li>기초설계 → 요소설계 → 종합설계 순서</li>
 *   <li>기초+요소, 요소+종합 병수 가능</li>
 *   <li>기초 이수 전 / 종합 이수 후 요소설계는 설계학점 불인정</li>
 *   <li>소속 학과 개설 교과만 인정 (departmentCourse)</li>
 * </ul>
 */
@Service
public class DesignCreditEvaluator {

    public DesignEvaluationResult evaluate(
            List<StudentEnrollment> enrollments,
            Map<String, CurriculumCourse> entranceCurriculumByCode
    ) {
        List<StudentEnrollment> passed = enrollments.stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> e.getCourseMaster().isDepartmentCourse())
                .filter(e -> effectiveDesignCredits(e, entranceCurriculumByCode) > 0)
                .sorted(Comparator.comparingInt(StudentEnrollment::termKey))
                .toList();

        OptionalInt basicTerm = findFirstTerm(passed, entranceCurriculumByCode, DesignLevel.BASIC);
        OptionalInt comprehensiveTerm = findFirstTerm(passed, entranceCurriculumByCode, DesignLevel.COMPREHENSIVE);

        List<DesignCourseResult> details = new ArrayList<>();
        double recognized = 0;
        boolean hasBasic = false;
        boolean hasElement = false;
        boolean hasComprehensive = false;

        for (StudentEnrollment e : passed) {
            DesignLevel level = resolveDesignLevel(e, entranceCurriculumByCode);
            if (level == DesignLevel.NONE) {
                continue;
            }
            double rawDesign = effectiveDesignCredits(e, entranceCurriculumByCode);

            boolean counted;
            String reason;

            switch (level) {
                case BASIC -> {
                    counted = true;
                    reason = "기초설계 인정";
                    hasBasic = true;
                }
                case COMPREHENSIVE -> {
                    counted = true;
                    reason = "종합설계 인정";
                    hasComprehensive = true;
                }
                case ELEMENT -> {
                    if (basicTerm.isEmpty()) {
                        counted = false;
                        reason = "기초설계 미이수 — 요소설계 설계학점 불인정";
                    } else if (e.termKey() < basicTerm.getAsInt()) {
                        counted = false;
                        reason = "기초설계 이수 전 수강 — 요소설계 설계학점 불인정";
                    } else if (comprehensiveTerm.isPresent() && e.termKey() > comprehensiveTerm.getAsInt()) {
                        counted = false;
                        reason = "종합설계 이수 후 수강 — 요소설계 설계학점 불인정";
                    } else {
                        counted = true;
                        reason = e.termKey() == basicTerm.getAsInt()
                                ? "기초설계와 병수 — 요소설계 인정"
                                : (comprehensiveTerm.isPresent() && e.termKey() == comprehensiveTerm.getAsInt()
                                ? "종합설계와 병수 — 요소설계 인정"
                                : "요소설계 인정");
                        hasElement = true;
                    }
                }
                default -> {
                    counted = false;
                    reason = "설계 아님";
                }
            }

            double design = counted ? rawDesign : 0;
            recognized += design;
            details.add(DesignCourseResult.builder()
                    .courseCode(e.getCourseMaster().getCourseCode())
                    .courseName(e.getCourseMaster().getName())
                    .takenYear(e.getTakenYear())
                    .takenSemester(e.getTakenSemester())
                    .designLevel(level)
                    .rawDesignCredits(rawDesign)
                    .recognizedDesignCredits(design)
                    .recognized(counted)
                    .reason(reason)
                    .build());
        }

        return DesignEvaluationResult.builder()
                .recognizedDesignCredits(recognized)
                .hasBasicDesign(hasBasic)
                .hasElementDesign(hasElement)
                .hasComprehensiveDesign(hasComprehensive)
                .sequenceSatisfied(hasBasic && hasElement && hasComprehensive)
                .courses(details)
                .build();
    }

    private OptionalInt findFirstTerm(
            List<StudentEnrollment> passed,
            Map<String, CurriculumCourse> curriculum,
            DesignLevel target
    ) {
        return passed.stream()
                .filter(e -> resolveDesignLevel(e, curriculum) == target)
                .mapToInt(StudentEnrollment::termKey)
                .min();
    }

    private double effectiveDesignCredits(
            StudentEnrollment e,
            Map<String, CurriculumCourse> curriculum
    ) {
        if (e.getDesignCredits() > 0) {
            return e.getDesignCredits();
        }
        CurriculumCourse matched = findCurriculumCourse(e, curriculum);
        return matched == null ? 0.0 : matched.getDesignCredits();
    }

    private CurriculumCourse findCurriculumCourse(
            StudentEnrollment e,
            Map<String, CurriculumCourse> curriculum
    ) {
        CurriculumCourse byCode = curriculum.get(e.getCourseMaster().getCourseCode());
        if (byCode != null) {
            return byCode;
        }
        String group = e.getCourseMaster().getEquivalenceGroup();
        if (group != null) {
            for (CurriculumCourse c : curriculum.values()) {
                if (group.equals(c.getCourseMaster().getEquivalenceGroup())) {
                    return c;
                }
            }
        }
        String enrollName = normalizeName(e.getCourseMaster().getName());
        if (!enrollName.isBlank()) {
            for (CurriculumCourse c : curriculum.values()) {
                if (enrollName.equals(normalizeName(c.getCourseMaster().getName()))) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * 수강 과목의 설계 단계는 입학 연도 교과과정에서 우선 조회.
     * 코드가 다르더라도 과목명·동등그룹이 같으면 동일 단계로 본다.
     * 없으면 설계학점 &gt; 0 이면 이름 휴리스틱 / ELEMENT.
     */
    private DesignLevel resolveDesignLevel(StudentEnrollment e, Map<String, CurriculumCourse> curriculum) {
        CurriculumCourse cc = findCurriculumCourse(e, curriculum);
        if (cc != null && cc.getDesignLevel() != DesignLevel.NONE) {
            return cc.getDesignLevel();
        }
        double designCredits = effectiveDesignCredits(e, curriculum);
        if (designCredits > 0) {
            String name = e.getCourseMaster().getName();
            if (name.contains("Capstone") || name.contains("종합설계")) {
                return DesignLevel.COMPREHENSIVE;
            }
            if (name.contains("공학설계기초") || name.contains("산학프로젝트입문")) {
                return DesignLevel.BASIC;
            }
            return DesignLevel.ELEMENT;
        }
        return DesignLevel.NONE;
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

    public Map<String, CurriculumCourse> indexByCode(List<CurriculumCourse> courses) {
        return courses.stream().collect(Collectors.toMap(
                c -> c.getCourseMaster().getCourseCode(),
                c -> c,
                (a, b) -> a
        ));
    }
}
