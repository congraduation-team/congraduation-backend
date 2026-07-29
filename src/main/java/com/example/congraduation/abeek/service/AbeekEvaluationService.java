package com.example.congraduation.abeek.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.congraduation.abeek.domain.*;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.dto.*;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.abeek.repository.AbeekStudentRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbeekEvaluationService {

    private final AbeekStudentRepository studentRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AdvantageousRequirementService advantageousRequirementService;
    private final DesignCreditEvaluator designCreditEvaluator;

    @Transactional(readOnly = true)
    public AbeekEvaluationResponse evaluate(String studentId) {
        AbeekStudent student = studentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 없음: " + studentId));

        EffectiveAbeekRequirement effective = advantageousRequirementService.resolve(
                student.getDepartmentCode(), student.getEntranceYear(), student.getGraduationAbeekYear());

        List<CurriculumCourse> entranceCourses =
                curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(
                        student.getDepartmentCode(), student.getEntranceYear());
        List<CurriculumCourse> graduationCourses =
                curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(
                        student.getDepartmentCode(), student.getGraduationAbeekYear());

        Map<String, CurriculumCourse> entranceByCode = designCreditEvaluator.indexByCode(entranceCourses);
        Set<String> completedGroups = completedEquivalenceGroups(student.getEnrollments());
        Set<String> completedCodes = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> e.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());

        DesignEvaluationResult designResult =
                designCreditEvaluator.evaluate(student.getEnrollments(), entranceByCode);

        int generalCredits = sumCredits(student, CourseCategory.GENERAL);
        int bsmCredits = sumCredits(student, CourseCategory.BSM);
        int majorCredits = sumCredits(student, CourseCategory.MAJOR);

        CategoryProgressDto general = progress("전문교양", generalCredits, effective.getGeneralMinCredits(), effective.getGeneralSource());
        CategoryProgressDto bsm = progress("BSM", bsmCredits, effective.getBsmMinCredits(), effective.getBsmSource());
        CategoryProgressDto major = progress("전공", majorCredits, effective.getMajorMinCredits(), effective.getMajorSource());
        CategoryProgressDto design = progress("설계", designResult.getRecognizedDesignCredits(),
                effective.getDesignMinCredits(), effective.getDesignSource());

        List<RequiredCourseStatusDto> entranceRequired = evaluateEntranceRequired(
                entranceCourses, completedCodes, completedGroups);

        List<RequiredCourseStatusDto> waived = findWaivedGraduationOnlyRequired(
                entranceCourses, graduationCourses, completedCodes, completedGroups);

        List<String> messages = new ArrayList<>();
        messages.add(String.format(
                "입학 %d년도 vs 졸업ABEEK %d년도 → 설계 최소 %.1f학점 (%s)",
                student.getEntranceYear(), student.getGraduationAbeekYear(),
                effective.getDesignMinCredits(), effective.getDesignSource()));
        messages.add(String.format(
                "전공 최소 %d학점 (%s), 전문교양 %d, BSM %d",
                effective.getMajorMinCredits(), effective.getMajorSource(),
                effective.getGeneralMinCredits(), effective.getBsmMinCredits()));

        if (!designResult.isHasBasicDesign()) {
            messages.add("기초설계(공학설계기초) 미이수");
        }
        if (!designResult.isHasElementDesign()) {
            messages.add("인정되는 요소설계 미이수");
        }
        if (!designResult.isHasComprehensiveDesign()) {
            messages.add("종합설계(Capstone) 미이수");
        }
        if (!waived.isEmpty()) {
            messages.add("졸업연도 신설 필수이나 입학연도에 없어 면제: "
                    + waived.stream().map(RequiredCourseStatusDto::getCourseName).collect(Collectors.joining(", ")));
        }

        boolean certElectiveOk = checkCertElective(student, entranceCourses, effective, messages);

        boolean requiredOk = entranceRequired.stream()
                .filter(r -> !r.isWaived())
                .allMatch(RequiredCourseStatusDto::isCompleted);

        boolean overall = general.isSatisfied()
                && bsm.isSatisfied()
                && major.isSatisfied()
                && design.isSatisfied()
                && designResult.isSequenceSatisfied()
                && requiredOk
                && certElectiveOk;

        return AbeekEvaluationResponse.builder()
                .studentId(student.getStudentId())
                .studentNo(student.getStudentId())
                .studentName(student.getName())
                .entranceYear(student.getEntranceYear())
                .graduationAbeekYear(student.getGraduationAbeekYear())
                .overallSatisfied(overall)
                .general(general)
                .bsm(bsm)
                .major(major)
                .design(design)
                .designSequenceSatisfied(designResult.isSequenceSatisfied())
                .designDetail(designResult)
                .entranceRequiredCourses(entranceRequired)
                .waivedGraduationOnlyCourses(waived)
                .messages(messages)
                .build();
    }

    private boolean checkCertElective(
            AbeekStudent student,
            List<CurriculumCourse> entranceCourses,
            EffectiveAbeekRequirement effective,
            List<String> messages
    ) {
        if (effective.getCertElectiveMinCourses() <= 0) {
            return true;
        }

        Set<String> electiveCodes = entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.CERT_ELECTIVE)
                .map(c -> c.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());

        // 입학 연도에 인증선택이 없으면 졸업 연도 인증선택 목록을 참고하지 않고 통과
        // (2020~2021은 지정 목록 이수 방식)
        List<StudentEnrollment> taken = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> electiveCodes.contains(e.getCourseMaster().getCourseCode())
                        || e.getCourseMaster().getElectiveArea() != com.example.congraduation.abeek.domain.enums.ElectiveArea.NONE)
                .toList();

        int courseCount = (int) taken.stream()
                .map(e -> e.getCourseMaster().getCourseCode())
                .distinct()
                .count();
        int creditSum = taken.stream().mapToInt(StudentEnrollment::getCredits).sum();
        long areas = taken.stream()
                .map(e -> e.getCourseMaster().getElectiveArea())
                .filter(a -> a != com.example.congraduation.abeek.domain.enums.ElectiveArea.NONE)
                .distinct()
                .count();

        boolean ok = courseCount >= effective.getCertElectiveMinCourses()
                && creditSum >= effective.getCertElectiveMinCredits();

        if (effective.getCertElectiveMinAreas() > 0 && areas < effective.getCertElectiveMinAreas()) {
            messages.add(String.format(
                    "인증선택 영역 권장/요건: %d개 영역 중 현재 %d개",
                    effective.getCertElectiveMinAreas(), areas));
            // 2026은 권장이므로 영역은 soft — 학점/과목 수만 hard
        }

        if (!ok) {
            messages.add(String.format(
                    "인증선택 부족: %d과목/%d학점 (필요 %d과목/%d학점)",
                    courseCount, creditSum,
                    effective.getCertElectiveMinCourses(), effective.getCertElectiveMinCredits()));
        }
        return ok;
    }

    private List<RequiredCourseStatusDto> evaluateEntranceRequired(
            List<CurriculumCourse> entranceCourses,
            Set<String> completedCodes,
            Set<String> completedGroups
    ) {
        return entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.REQUIRED || c.getRole() == CourseRole.BSM_REQUIRED)
                .map(c -> {
                    boolean done = isCompleted(c.getCourseMaster(), completedCodes, completedGroups);
                    return RequiredCourseStatusDto.builder()
                            .courseCode(c.getCourseMaster().getCourseCode())
                            .courseName(c.getCourseMaster().getName())
                            .completed(done)
                            .waived(false)
                            .note(done ? "이수" : "미이수")
                            .build();
                })
                .toList();
    }

    /**
     * 졸업 연도에만 있는 필수(신설) → 입학 연도에 없으면 면제.
     */
    private List<RequiredCourseStatusDto> findWaivedGraduationOnlyRequired(
            List<CurriculumCourse> entranceCourses,
            List<CurriculumCourse> graduationCourses,
            Set<String> completedCodes,
            Set<String> completedGroups
    ) {
        Set<String> entranceRequiredGroups = entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.REQUIRED)
                .map(c -> Optional.ofNullable(c.getCourseMaster().getEquivalenceGroup())
                        .orElse(c.getCourseMaster().getCourseCode()))
                .collect(Collectors.toSet());

        Set<String> entranceCodes = entranceCourses.stream()
                .map(c -> c.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());

        return graduationCourses.stream()
                .filter(c -> c.getRole() == CourseRole.REQUIRED)
                .filter(c -> c.isNewlyIntroducedRequired()
                        || !entranceCodes.contains(c.getCourseMaster().getCourseCode()))
                .filter(c -> {
                    String g = Optional.ofNullable(c.getCourseMaster().getEquivalenceGroup())
                            .orElse(c.getCourseMaster().getCourseCode());
                    return !entranceRequiredGroups.contains(g);
                })
                .map(c -> RequiredCourseStatusDto.builder()
                        .courseCode(c.getCourseMaster().getCourseCode())
                        .courseName(c.getCourseMaster().getName())
                        .completed(isCompleted(c.getCourseMaster(), completedCodes, completedGroups))
                        .waived(true)
                        .note("입학 연도 교과과정에 없는 신설 필수 → 면제")
                        .build())
                .toList();
    }

    private boolean isCompleted(CourseMaster master, Set<String> codes, Set<String> groups) {
        if (codes.contains(master.getCourseCode())) {
            return true;
        }
        return master.getEquivalenceGroup() != null && groups.contains(master.getEquivalenceGroup());
    }

    private Set<String> completedEquivalenceGroups(List<StudentEnrollment> enrollments) {
        return enrollments.stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> e.getCourseMaster().getEquivalenceGroup())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private int sumCredits(AbeekStudent student, CourseCategory category) {
        return student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> e.getCourseMaster().getCategory() == category)
                .mapToInt(StudentEnrollment::getCredits)
                .sum();
    }

    private CategoryProgressDto progress(String name, double earned, double required, String source) {
        return CategoryProgressDto.builder()
                .category(name)
                .earnedCredits(earned)
                .requiredCredits(required)
                .satisfied(earned + 1e-9 >= required)
                .requirementSource(source)
                .build();
    }
}
