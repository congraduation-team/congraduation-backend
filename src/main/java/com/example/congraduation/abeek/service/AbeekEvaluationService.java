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

    @Transactional
    public AbeekEvaluationResponse evaluate(String studentId) {
        AbeekStudent student = studentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 없음: " + studentId));

        // 기이수(수강이력) 마지막 학기 연도 = 졸업 ABEEK 연도 (1학기/2학기 모두 그 해)
        int graduationAbeekYear = resolveGraduationAbeekYearFromEnrollments(student);
        if (graduationAbeekYear != student.getGraduationAbeekYear()) {
            student.setGraduationAbeekYear(graduationAbeekYear);
            studentRepository.save(student);
        }

        EffectiveAbeekRequirement effective = advantageousRequirementService.resolve(
                student.getDepartmentCode(), student.getEntranceYear(), graduationAbeekYear);

        List<CurriculumCourse> entranceCourses =
                curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(
                        student.getDepartmentCode(), student.getEntranceYear());
        List<CurriculumCourse> graduationCourses =
                curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(
                        student.getDepartmentCode(), graduationAbeekYear);

        Map<String, CurriculumCourse> entranceByCode = designCreditEvaluator.indexByCode(entranceCourses);
        Set<String> completedGroups = completedEquivalenceGroups(student.getEnrollments());
        Set<String> completedCodes = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> e.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());
        Set<String> completedNames = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> normalizeCourseName(e.getCourseMaster().getName()))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());

        DesignEvaluationResult designResult =
                designCreditEvaluator.evaluate(student.getEnrollments(), entranceByCode);

        int generalCredits = sumCredits(student, CourseCategory.GENERAL, entranceCourses, graduationCourses);
        int bsmCredits = sumCredits(student, CourseCategory.BSM, entranceCourses, graduationCourses);
        int majorCredits = sumCredits(student, CourseCategory.MAJOR, entranceCourses, graduationCourses);

        CategoryProgressDto general = progress("전문교양", generalCredits, effective.getGeneralMinCredits(), effective.getGeneralSource());
        CategoryProgressDto bsm = progress("BSM", bsmCredits, effective.getBsmMinCredits(), effective.getBsmSource());
        CategoryProgressDto major = progress("전공", majorCredits, effective.getMajorMinCredits(), effective.getMajorSource());
        CategoryProgressDto design = progress("설계", designResult.getRecognizedDesignCredits(),
                effective.getDesignMinCredits(), effective.getDesignSource());

        List<RequiredCourseStatusDto> entranceRequired = evaluateEntranceRequired(
                entranceCourses, completedCodes, completedGroups, completedNames);

        List<RequiredCourseStatusDto> waived = findWaivedGraduationOnlyRequired(
                entranceCourses, graduationCourses, completedCodes, completedGroups, completedNames);

        List<String> notes = new ArrayList<>();
        if (!designResult.isHasBasicDesign()) {
            notes.add("기초설계 미이수");
        }
        if (!designResult.isHasElementDesign()) {
            notes.add("요소설계 미이수");
        }
        if (!designResult.isHasComprehensiveDesign()) {
            notes.add("종합설계(Capstone) 미이수");
        }

        List<String> waivedNames = waived.stream()
                .map(RequiredCourseStatusDto::getCourseName)
                .toList();
        if (!waivedNames.isEmpty()) {
            notes.add("졸업연도 신설 필수이나 입학연도에 없어 면제: " + String.join(", ", waivedNames));
        }

        String appliedBasis = String.format("입학 %d / 졸업ABEEK %d 중 유리한 기준 적용",
                student.getEntranceYear(), graduationAbeekYear);

        AbeekEvaluationResponse.RequirementSummaryDto requirementSummary =
                AbeekEvaluationResponse.RequirementSummaryDto.builder()
                        .entranceYear(student.getEntranceYear())
                        .graduationAbeekYear(graduationAbeekYear)
                        .appliedBasis(appliedBasis)
                        .generalMinCredits(effective.getGeneralMinCredits())
                        .bsmMinCredits(effective.getBsmMinCredits())
                        .majorMinCredits(effective.getMajorMinCredits())
                        .designMinCredits(effective.getDesignMinCredits())
                        .certElectiveApplicable(effective.getCertElectiveMinCourses() > 0)
                        .certElectiveMinCourses(effective.getCertElectiveMinCourses())
                        .certElectiveMinCredits(effective.getCertElectiveMinCredits())
                        .designSequenceSatisfied(designResult.isSequenceSatisfied())
                        .hasBasicDesign(designResult.isHasBasicDesign())
                        .hasElementDesign(designResult.isHasElementDesign())
                        .hasComprehensiveDesign(designResult.isHasComprehensiveDesign())
                        .waivedCourses(waivedNames)
                        .notes(notes)
                        .build();

        List<String> messages = new ArrayList<>(notes);

        boolean certElectiveOk = checkCertElective(student, entranceCourses, effective, messages);
        boolean certElectiveApplicable = effective.getCertElectiveMinCourses() > 0;
        CategoryProgressDto certElective = buildCertElectiveProgress(
                student, entranceCourses, effective, certElectiveApplicable);

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
                .graduationAbeekYear(graduationAbeekYear)
                .overallSatisfied(overall)
                .general(general)
                .bsm(bsm)
                .major(major)
                .design(design)
                .certElectiveApplicable(certElectiveApplicable)
                .certElective(certElective)
                .designSequenceSatisfied(designResult.isSequenceSatisfied())
                .designDetail(designResult)
                .entranceRequiredCourses(entranceRequired)
                .waivedGraduationOnlyCourses(waived)
                .requirementSummary(requirementSummary)
                .messages(messages)
                .build();
    }

    @Transactional
    public AbeekEvaluationDetailResponse evaluateDetail(String studentId) {
        AbeekEvaluationResponse evaluation = evaluate(studentId);
        AbeekStudent student = studentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 없음: " + studentId));

        List<CurriculumCourse> entranceCourses = curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(
                student.getDepartmentCode(), student.getEntranceYear());
        List<CurriculumCourse> graduationCourses = curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(
                student.getDepartmentCode(), evaluation.getGraduationAbeekYear());

        Map<String, CourseCategory> categoryByCode = new HashMap<>();
        Map<String, CourseCategory> categoryByGroup = new HashMap<>();
        Map<String, CourseCategory> categoryByName = new HashMap<>();
        for (CurriculumCourse course : entranceCourses) {
            indexCategory(course, categoryByCode, categoryByGroup, categoryByName);
        }
        for (CurriculumCourse course : graduationCourses) {
            indexCategory(course, categoryByCode, categoryByGroup, categoryByName);
        }

        Set<String> completedCodes = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> e.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());
        Set<String> completedGroups = completedEquivalenceGroups(student.getEnrollments());
        Set<String> completedNames = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> normalizeCourseName(e.getCourseMaster().getName()))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());

        List<AbeekEvaluationDetailResponse.CategoryDetailDto> categories = List.of(
                buildCategoryDetail(
                        "GENERAL",
                        "전문교양",
                        evaluation.getGeneral(),
                        student,
                        entranceCourses,
                        completedCodes,
                        completedGroups,
                        completedNames,
                        categoryByCode,
                        categoryByGroup,
                        categoryByName
                ),
                buildCategoryDetail(
                        "BSM",
                        "BSM",
                        evaluation.getBsm(),
                        student,
                        entranceCourses,
                        completedCodes,
                        completedGroups,
                        completedNames,
                        categoryByCode,
                        categoryByGroup,
                        categoryByName
                ),
                buildCategoryDetail(
                        "MAJOR",
                        "전공",
                        evaluation.getMajor(),
                        student,
                        entranceCourses,
                        completedCodes,
                        completedGroups,
                        completedNames,
                        categoryByCode,
                        categoryByGroup,
                        categoryByName
                ),
                buildCertElectiveDetail(student, entranceCourses, evaluation.isCertElectiveApplicable(), evaluation.getCertElective())
        );

        List<AbeekEvaluationDetailResponse.CourseDetailDto> allCompleted = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .sorted(Comparator.comparingInt(StudentEnrollment::getTakenYear)
                        .thenComparingInt(StudentEnrollment::getTakenSemester)
                        .thenComparing(e -> e.getCourseMaster().getName()))
                .map(e -> {
                    CourseCategory cat = resolveCategory(e.getCourseMaster(), categoryByCode, categoryByGroup, categoryByName);
                    String catLabel = categoryLabel(cat);
                    return AbeekEvaluationDetailResponse.CourseDetailDto.builder()
                            .courseCode(e.getCourseMaster().getCourseCode())
                            .courseName(e.getCourseMaster().getName())
                            .category(cat)
                            .categoryLabel(catLabel)
                            .role(resolveRole(e.getCourseMaster(), entranceCourses))
                            .roleLabel(roleLabel(resolveRole(e.getCourseMaster(), entranceCourses)))
                            .credits(e.getCredits())
                            .designCredits(e.getDesignCredits())
                            .designLevel(resolveDesignLevelForDetail(e, entranceCourses))
                            .completed(true)
                            .waived(false)
                            .takenYear(e.getTakenYear())
                            .takenSemester(e.getTakenSemester())
                            .note("이수")
                            .build();
                })
                .toList();

        return AbeekEvaluationDetailResponse.builder()
                .studentId(evaluation.getStudentId())
                .studentNo(evaluation.getStudentNo())
                .studentName(evaluation.getStudentName())
                .entranceYear(evaluation.getEntranceYear())
                .graduationAbeekYear(evaluation.getGraduationAbeekYear())
                .evaluation(evaluation)
                .allCompletedCourses(allCompleted)
                .allCompletedCourseCount(allCompleted.size())
                .categories(categories)
                .build();
    }

    private CategoryProgressDto buildCertElectiveProgress(
            AbeekStudent student,
            List<CurriculumCourse> entranceCourses,
            EffectiveAbeekRequirement effective,
            boolean applicable
    ) {
        if (!applicable) {
            return progress("인증선택", 0, 0, student.getEntranceYear() + "년도(해당없음)");
        }

        Set<String> electiveCodes = entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.CERT_ELECTIVE)
                .map(c -> c.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());

        int creditSum = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> electiveCodes.contains(e.getCourseMaster().getCourseCode())
                        || e.getCourseMaster().getElectiveArea() != com.example.congraduation.abeek.domain.enums.ElectiveArea.NONE)
                .mapToInt(StudentEnrollment::getCredits)
                .sum();

        return progress(
                "인증선택",
                creditSum,
                effective.getCertElectiveMinCredits(),
                "입학 " + student.getEntranceYear() + "년도");
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

    private AbeekEvaluationDetailResponse.CategoryDetailDto buildCategoryDetail(
            String categoryKey,
            String categoryLabel,
            CategoryProgressDto progress,
            AbeekStudent student,
            List<CurriculumCourse> entranceCourses,
            Set<String> completedCodes,
            Set<String> completedGroups,
            Set<String> completedNames,
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName
    ) {
        CourseCategory targetCategory = parseCategoryKey(categoryKey);

        List<AbeekEvaluationDetailResponse.CourseDetailDto> completedCourses = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> resolveCategory(e.getCourseMaster(), categoryByCode, categoryByGroup, categoryByName) == targetCategory)
                .sorted(Comparator.comparingInt(StudentEnrollment::getTakenYear)
                        .thenComparingInt(StudentEnrollment::getTakenSemester)
                        .thenComparing(e -> e.getCourseMaster().getName()))
                .map(e -> AbeekEvaluationDetailResponse.CourseDetailDto.builder()
                        .courseCode(e.getCourseMaster().getCourseCode())
                        .courseName(e.getCourseMaster().getName())
                        .category(targetCategory)
                        .categoryLabel(categoryLabel)
                        .role(resolveRole(e.getCourseMaster(), entranceCourses))
                        .roleLabel(roleLabel(resolveRole(e.getCourseMaster(), entranceCourses)))
                        .credits(e.getCredits())
                        .designCredits(e.getDesignCredits())
                        .designLevel(resolveDesignLevelForDetail(e, entranceCourses))
                        .completed(true)
                        .waived(false)
                        .takenYear(e.getTakenYear())
                        .takenSemester(e.getTakenSemester())
                        .note("이수")
                        .build())
                .toList();

        List<AbeekEvaluationDetailResponse.CourseDetailDto> remainingCourses = entranceCourses.stream()
                .filter(c -> c.getCourseMaster().getCategory() == targetCategory)
                .filter(c -> !isCompleted(c.getCourseMaster(), completedCodes, completedGroups, completedNames))
                .sorted(Comparator.comparing(CurriculumCourse::getRecommendedTerm, Comparator.nullsLast(String::compareTo))
                        .thenComparing(c -> c.getCourseMaster().getName()))
                .map(c -> AbeekEvaluationDetailResponse.CourseDetailDto.builder()
                        .courseCode(c.getCourseMaster().getCourseCode())
                        .courseName(c.getCourseMaster().getName())
                        .category(targetCategory)
                        .categoryLabel(categoryLabel)
                        .role(c.getRole())
                        .roleLabel(roleLabel(c.getRole()))
                        .credits(c.getCredits())
                        .designCredits(c.getDesignCredits())
                        .designLevel(c.getDesignLevel())
                        .completed(false)
                        .waived(false)
                        .takenYear(null)
                        .takenSemester(null)
                        .note(c.getRecommendedTerm())
                        .build())
                .toList();

        return AbeekEvaluationDetailResponse.CategoryDetailDto.builder()
                .categoryKey(categoryKey)
                .categoryLabel(categoryLabel)
                .progress(progress)
                .completedCourseCount(completedCourses.size())
                .completedCourses(completedCourses)
                .remainingCourses(remainingCourses)
                .build();
    }

    private AbeekEvaluationDetailResponse.CategoryDetailDto buildCertElectiveDetail(
            AbeekStudent student,
            List<CurriculumCourse> entranceCourses,
            boolean applicable,
            CategoryProgressDto progress
    ) {
        if (!applicable) {
            return AbeekEvaluationDetailResponse.CategoryDetailDto.builder()
                    .categoryKey("CERT_ELECTIVE")
                    .categoryLabel("인증선택")
                    .progress(progress)
                    .completedCourseCount(0)
                    .completedCourses(List.of())
                    .remainingCourses(List.of())
                    .build();
        }

        Set<String> electiveCodes = entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.CERT_ELECTIVE)
                .map(c -> c.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());

        List<AbeekEvaluationDetailResponse.CourseDetailDto> completedCourses = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> electiveCodes.contains(e.getCourseMaster().getCourseCode())
                        || e.getCourseMaster().getElectiveArea() != com.example.congraduation.abeek.domain.enums.ElectiveArea.NONE)
                .sorted(Comparator.comparingInt(StudentEnrollment::getTakenYear)
                        .thenComparingInt(StudentEnrollment::getTakenSemester)
                        .thenComparing(e -> e.getCourseMaster().getName()))
                .map(e -> AbeekEvaluationDetailResponse.CourseDetailDto.builder()
                        .courseCode(e.getCourseMaster().getCourseCode())
                        .courseName(e.getCourseMaster().getName())
                        .category(CourseCategory.GENERAL)
                        .categoryLabel("인증선택")
                        .role(CourseRole.CERT_ELECTIVE)
                        .roleLabel("인증선택")
                        .credits(e.getCredits())
                        .designCredits(0)
                        .designLevel(null)
                        .completed(true)
                        .waived(false)
                        .takenYear(e.getTakenYear())
                        .takenSemester(e.getTakenSemester())
                        .note("이수")
                        .build())
                .toList();

        List<AbeekEvaluationDetailResponse.CourseDetailDto> remainingCourses = entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.CERT_ELECTIVE)
                .map(c -> AbeekEvaluationDetailResponse.CourseDetailDto.builder()
                        .courseCode(c.getCourseMaster().getCourseCode())
                        .courseName(c.getCourseMaster().getName())
                        .category(CourseCategory.GENERAL)
                        .categoryLabel("인증선택")
                        .role(CourseRole.CERT_ELECTIVE)
                        .roleLabel("인증선택")
                        .credits(c.getCredits())
                        .designCredits(0)
                        .designLevel(null)
                        .completed(false)
                        .waived(false)
                        .takenYear(null)
                        .takenSemester(null)
                        .note(c.getRecommendedTerm())
                        .build())
                .toList();

        return AbeekEvaluationDetailResponse.CategoryDetailDto.builder()
                .categoryKey("CERT_ELECTIVE")
                .categoryLabel("인증선택")
                .progress(progress)
                .completedCourseCount(completedCourses.size())
                .completedCourses(completedCourses)
                .remainingCourses(remainingCourses)
                .build();
    }

    private List<RequiredCourseStatusDto> evaluateEntranceRequired(
            List<CurriculumCourse> entranceCourses,
            Set<String> completedCodes,
            Set<String> completedGroups,
            Set<String> completedNames
    ) {
        return entranceCourses.stream()
                .filter(c -> c.getRole() == CourseRole.REQUIRED || c.getRole() == CourseRole.BSM_REQUIRED)
                .map(c -> {
                    boolean done = isCompleted(c.getCourseMaster(), completedCodes, completedGroups, completedNames);
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
            Set<String> completedGroups,
            Set<String> completedNames
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
                        .completed(isCompleted(c.getCourseMaster(), completedCodes, completedGroups, completedNames))
                        .waived(true)
                        .note("입학 연도 교과과정에 없는 신설 필수 → 면제")
                        .build())
                .toList();
    }

    private int resolveGraduationAbeekYearFromEnrollments(AbeekStudent student) {
        if (student.getEnrollments() == null || student.getEnrollments().isEmpty()) {
            return student.getGraduationAbeekYear();
        }
        return student.getEnrollments().stream()
                .map(e -> new int[]{e.getTakenYear(), e.getTakenSemester() <= 1 ? 1 : 3})
                .max(Comparator
                        .comparingInt((int[] t) -> t[0])
                        .thenComparingInt(t -> t[1]))
                .map(t -> t[0])
                .orElse(student.getGraduationAbeekYear());
    }

    private boolean isCompleted(
            CourseMaster master,
            Set<String> codes,
            Set<String> groups,
            Set<String> completedNames
    ) {
        if (codes.contains(master.getCourseCode())) {
            return true;
        }
        if (master.getEquivalenceGroup() != null && groups.contains(master.getEquivalenceGroup())) {
            return true;
        }
        return completedNames.contains(normalizeCourseName(master.getName()));
    }

    private Set<String> completedEquivalenceGroups(List<StudentEnrollment> enrollments) {
        return enrollments.stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> e.getCourseMaster().getEquivalenceGroup())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private int sumCredits(
            AbeekStudent student,
            CourseCategory category,
            List<CurriculumCourse> entranceCourses,
            List<CurriculumCourse> graduationCourses
    ) {
        Map<String, CourseCategory> categoryByCode = new HashMap<>();
        Map<String, CourseCategory> categoryByGroup = new HashMap<>();
        Map<String, CourseCategory> categoryByName = new HashMap<>();
        for (CurriculumCourse course : entranceCourses) {
            indexCategory(course, categoryByCode, categoryByGroup, categoryByName);
        }
        for (CurriculumCourse course : graduationCourses) {
            indexCategory(course, categoryByCode, categoryByGroup, categoryByName);
        }

        return student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> resolveCategory(e.getCourseMaster(), categoryByCode, categoryByGroup, categoryByName)
                        == category)
                .mapToInt(StudentEnrollment::getCredits)
                .sum();
    }

    private void indexCategory(
            CurriculumCourse course,
            Map<String, CourseCategory> byCode,
            Map<String, CourseCategory> byGroup,
            Map<String, CourseCategory> byName
    ) {
        CourseMaster master = course.getCourseMaster();
        CourseCategory category = master.getCategory();
        byCode.putIfAbsent(master.getCourseCode(), category);
        if (master.getEquivalenceGroup() != null && !master.getEquivalenceGroup().isBlank()) {
            byGroup.putIfAbsent(master.getEquivalenceGroup(), category);
        }
        String normalized = normalizeCourseName(master.getName());
        if (!normalized.isBlank()) {
            byName.putIfAbsent(normalized, category);
        }
        String withoutParen = normalizeCourseName(
                master.getName() == null ? "" : master.getName().replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            byName.putIfAbsent(withoutParen, category);
        }
    }

    private CourseCategory resolveCategory(
            CourseMaster master,
            Map<String, CourseCategory> byCode,
            Map<String, CourseCategory> byGroup,
            Map<String, CourseCategory> byName
    ) {
        CourseCategory byExact = byCode.get(master.getCourseCode());
        if (byExact != null) {
            return byExact;
        }
        if (master.getEquivalenceGroup() != null) {
            CourseCategory byEq = byGroup.get(master.getEquivalenceGroup());
            if (byEq != null) {
                return byEq;
            }
        }
        String normalized = normalizeCourseName(master.getName());
        if (!normalized.isBlank()) {
            CourseCategory byNormalized = byName.get(normalized);
            if (byNormalized != null) {
                return byNormalized;
            }
        }
        String withoutParen = normalizeCourseName(
                master.getName() == null ? "" : master.getName().replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            CourseCategory byParen = byName.get(withoutParen);
            if (byParen != null) {
                return byParen;
            }
        }
        return master.getCategory();
    }

    private String normalizeCourseName(String name) {
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

    private String categoryLabel(CourseCategory category) {
        return switch (category) {
            case GENERAL -> "전문교양";
            case BSM -> "BSM";
            case MAJOR -> "전공";
        };
    }

    private CourseCategory parseCategoryKey(String categoryKey) {
        return switch (categoryKey) {
            case "GENERAL" -> CourseCategory.GENERAL;
            case "BSM" -> CourseCategory.BSM;
            case "MAJOR" -> CourseCategory.MAJOR;
            default -> throw new IllegalArgumentException("지원하지 않는 카테고리: " + categoryKey);
        };
    }

    private CourseRole resolveRole(CourseMaster master, List<CurriculumCourse> entranceCourses) {
        return entranceCourses.stream()
                .filter(c -> sameCourse(master, c.getCourseMaster()))
                .map(CurriculumCourse::getRole)
                .findFirst()
                .orElse(CourseRole.ELECTIVE);
    }

    private com.example.congraduation.abeek.domain.enums.DesignLevel resolveDesignLevelForDetail(
            StudentEnrollment enrollment,
            List<CurriculumCourse> entranceCourses
    ) {
        return entranceCourses.stream()
                .filter(c -> sameCourse(enrollment.getCourseMaster(), c.getCourseMaster()))
                .map(CurriculumCourse::getDesignLevel)
                .filter(level -> level != null)
                .findFirst()
                .orElse(null);
    }

    private boolean sameCourse(CourseMaster left, CourseMaster right) {
        if (left.getCourseCode().equals(right.getCourseCode())) {
            return true;
        }
        if (left.getEquivalenceGroup() != null && left.getEquivalenceGroup().equals(right.getEquivalenceGroup())) {
            return true;
        }
        return normalizeCourseName(left.getName()).equals(normalizeCourseName(right.getName()));
    }

    private String roleLabel(CourseRole role) {
        return switch (role) {
            case REQUIRED -> "인증필수";
            case CERT_ELECTIVE -> "인증선택";
            case ELECTIVE -> "전공선택";
            case BSM_REQUIRED -> "BSM필수";
        };
    }

    private CategoryProgressDto progress(String name, double earned, double required, String source) {
        double progressPercent = required <= 0
                ? 100.0
                : Math.min(100.0, (earned / required) * 100.0);
        return CategoryProgressDto.builder()
                .category(name)
                .earnedCredits(earned)
                .requiredCredits(required)
                .progressPercent(progressPercent)
                .satisfied(earned + 1e-9 >= required)
                .requirementSource(source)
                .build();
    }
}
