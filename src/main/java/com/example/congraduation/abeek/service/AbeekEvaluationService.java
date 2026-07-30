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
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.MajorCreditSummaryDto;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.transcript.MajorCreditSummaryService;
import com.example.congraduation.service.transcript.TranscriptStorageService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbeekEvaluationService {

    private final AbeekStudentRepository studentRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AdvantageousRequirementService advantageousRequirementService;
    private final DesignCreditEvaluator designCreditEvaluator;
    private final GraduationAbeekYearResolver graduationAbeekYearResolver;
    private final StudentRepository appStudentRepository;
    private final TranscriptStorageService transcriptStorageService;
    private final MajorCreditSummaryService majorCreditSummaryService;

    @Transactional
    public AbeekEvaluationResponse evaluate(String studentId) {
        AbeekStudent student = studentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 없음: " + studentId));

        int[] lastTerm = resolveLastTakenTerm(student);
        int graduationAbeekYear = lastTerm[0] > 0 ? lastTerm[0] : student.getGraduationAbeekYear();
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

        GraduationAbeekYearResolver.GraduationTiming timing =
                resolveGraduationTiming(student, entranceCourses, graduationCourses);
        int expectedGraduationYear = timing.expectedGraduationYear();
        graduationAbeekYear = timing.abeekYear();

        Map<String, CurriculumCourse> entranceByCode = designCreditEvaluator.indexByCode(entranceCourses);
        Set<String> completedGroups = completedEquivalenceGroups(student.getEnrollments());
        Set<String> completedCodes = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .map(e -> e.getCourseMaster().getCourseCode())
                .collect(Collectors.toSet());
        Set<String> completedNames = new LinkedHashSet<>();
        for (StudentEnrollment enrollment : student.getEnrollments()) {
            if (!enrollment.isPassed()) {
                continue;
            }
            addCompletionNameVariants(completedNames, enrollment.getCourseMaster().getName());
        }

        List<CompletedCourseUploadRowDto> transcriptRows = loadTranscriptRows(student.getStudentId());
        // 입학 전 계절학기 포함: 기이수 행이 있으면 인증필수 이수 판정에 반영
        mergeTranscriptCompletions(transcriptRows, completedCodes, completedGroups, completedNames, entranceCourses);

        DesignEvaluationResult designResult =
                designCreditEvaluator.evaluate(student.getEnrollments(), entranceByCode);

        Map<String, CourseCategory> categoryByCode = new HashMap<>();
        Map<String, CourseCategory> categoryByGroup = new HashMap<>();
        Map<String, CourseCategory> categoryByName = new HashMap<>();
        for (CurriculumCourse course : entranceCourses) {
            indexCategory(course, categoryByCode, categoryByGroup, categoryByName);
        }
        for (CurriculumCourse course : graduationCourses) {
            indexCategory(course, categoryByCode, categoryByGroup, categoryByName);
        }

        int generalCredits = sumCredits(student, CourseCategory.GENERAL, categoryByCode, categoryByGroup, categoryByName);
        int bsmFromCurriculum = sumCredits(student, CourseCategory.BSM, categoryByCode, categoryByGroup, categoryByName);
        int majorFromCurriculum = sumCredits(student, CourseCategory.MAJOR, categoryByCode, categoryByGroup, categoryByName);

        // 기이수 전필+전선이 전공 학점의 기준. 커리큘럼 매칭만 쓰면 전필이 빠져 전선만 잡히는 경우가 있음.
        MajorCreditSummaryDto transcriptMajor = transcriptRows.isEmpty()
                ? null
                : majorCreditSummaryService.summarize(transcriptRows);
        int majorCredits = majorFromCurriculum;
        if (transcriptMajor != null) {
            majorCredits = Math.max(majorFromCurriculum, (int) Math.round(transcriptMajor.totalMajorCredits()));
        }

        // BSM: enrollment 오매칭(전공기초→전선 등) 대비, 기이수 과목명이 BSM 커리큘럼과 맞으면 학점 보정
        int bsmFromTranscript = sumTranscriptCreditsMatchingCategory(
                transcriptRows, CourseCategory.BSM, categoryByCode, categoryByGroup, categoryByName);
        int bsmCredits = Math.max(bsmFromCurriculum, bsmFromTranscript);

        List<CategoryProgressDto.CompletedCourseDto> generalCompleted =
                listCompletedByCategory(student, CourseCategory.GENERAL, categoryByCode, categoryByGroup, categoryByName);
        List<CategoryProgressDto.CompletedCourseDto> bsmCompleted =
                listBsmCompletedCourses(student, categoryByCode, categoryByGroup, categoryByName, transcriptRows);
        List<CategoryProgressDto.CompletedCourseDto> majorCompleted =
                listMajorCompletedCourses(student, categoryByCode, categoryByGroup, categoryByName, transcriptRows);
        List<CategoryProgressDto.CompletedCourseDto> designCompleted = designResult.getCourses().stream()
                .filter(DesignCourseResult::isRecognized)
                .map(c -> CategoryProgressDto.CompletedCourseDto.builder()
                        .courseCode(c.getCourseCode())
                        .courseName(c.getCourseName())
                        .credits((int) Math.round(c.getRecognizedDesignCredits()))
                        .designCredits(c.getRecognizedDesignCredits())
                        .takenYear(c.getTakenYear())
                        .takenSemester(c.getTakenSemester())
                        .build())
                .toList();
        List<CategoryProgressDto.CompletedCourseDto> allCompleted = listAllCompleted(student);

        CategoryProgressDto general = progress("전문교양", generalCredits, effective.getGeneralMinCredits(),
                effective.getGeneralSource(), generalCompleted);
        CategoryProgressDto bsm = progress("BSM", bsmCredits, effective.getBsmMinCredits(),
                effective.getBsmSource(), bsmCompleted);
        CategoryProgressDto major = progress("전공", majorCredits, effective.getMajorMinCredits(),
                effective.getMajorSource(), majorCompleted);
        CategoryProgressDto design = progress("설계", designResult.getRecognizedDesignCredits(),
                effective.getDesignMinCredits(), effective.getDesignSource(), designCompleted);

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
            notes.add("졸업예정 연도 신설 필수이나 입학연도에 없어 면제: " + String.join(", ", waivedNames));
        }

        String graduationAbeekBasisLabel = graduationAbeekYearResolver.basisLabel(expectedGraduationYear);
        String appliedBasis = String.format(
                "입학 %d / 졸업예정 %d (공학인증 %d) 중 유리한 기준 적용",
                student.getEntranceYear(), expectedGraduationYear, graduationAbeekYear);

        AbeekEvaluationResponse.RequirementSummaryDto requirementSummary =
                AbeekEvaluationResponse.RequirementSummaryDto.builder()
                        .entranceYear(student.getEntranceYear())
                        .graduationAbeekYear(graduationAbeekYear)
                        .expectedGraduationYear(expectedGraduationYear)
                        .graduationAbeekBasisLabel(graduationAbeekBasisLabel)
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
                .expectedGraduationYear(expectedGraduationYear)
                .graduationAbeekBasisLabel(graduationAbeekBasisLabel)
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
                .allCompletedCourseCount(allCompleted.size())
                .allCompletedCourses(allCompleted)
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

        // 전공은 전필+전선 합산 목록(기이수 보정 포함)을 상세에도 동일하게 쓴다.
        categories = withMajorCompletedFromEvaluation(categories, evaluation);

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
                .expectedGraduationYear(evaluation.getExpectedGraduationYear())
                .graduationAbeekBasisLabel(evaluation.getGraduationAbeekBasisLabel())
                .evaluation(evaluation)
                .allCompletedCourses(allCompleted)
                .allCompletedCourseCount(allCompleted.size())
                .categories(categories)
                .build();
    }

    private List<AbeekEvaluationDetailResponse.CategoryDetailDto> withMajorCompletedFromEvaluation(
            List<AbeekEvaluationDetailResponse.CategoryDetailDto> categories,
            AbeekEvaluationResponse evaluation
    ) {
        if (evaluation.getMajor() == null || evaluation.getMajor().getCompletedCourses() == null) {
            return categories;
        }
        List<AbeekEvaluationDetailResponse.CourseDetailDto> majorCourses = evaluation.getMajor().getCompletedCourses()
                .stream()
                .map(c -> AbeekEvaluationDetailResponse.CourseDetailDto.builder()
                        .courseCode(c.getCourseCode())
                        .courseName(c.getCourseName())
                        .category(CourseCategory.MAJOR)
                        .categoryLabel("전공")
                        .role(CourseRole.ELECTIVE)
                        .roleLabel("전공(전필/전선)")
                        .credits(c.getCredits())
                        .designCredits(c.getDesignCredits())
                        .designLevel(null)
                        .completed(true)
                        .waived(false)
                        .takenYear(c.getTakenYear())
                        .takenSemester(c.getTakenSemester())
                        .note("이수")
                        .build())
                .toList();

        List<AbeekEvaluationDetailResponse.CategoryDetailDto> replaced = new ArrayList<>();
        for (AbeekEvaluationDetailResponse.CategoryDetailDto category : categories) {
            if ("MAJOR".equals(category.getCategoryKey())) {
                replaced.add(AbeekEvaluationDetailResponse.CategoryDetailDto.builder()
                        .categoryKey(category.getCategoryKey())
                        .categoryLabel(category.getCategoryLabel())
                        .progress(evaluation.getMajor())
                        .completedCourseCount(majorCourses.size())
                        .completedCourses(majorCourses)
                        .remainingCourses(category.getRemainingCourses())
                        .build());
            } else {
                replaced.add(category);
            }
        }
        return replaced;
    }

    private CategoryProgressDto buildCertElectiveProgress(
            AbeekStudent student,
            List<CurriculumCourse> entranceCourses,
            EffectiveAbeekRequirement effective,
            boolean applicable
    ) {
        if (!applicable) {
            return progress("인증선택", 0, 0, student.getEntranceYear() + "년도(해당없음)", List.of());
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

        List<CategoryProgressDto.CompletedCourseDto> completed = student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> electiveCodes.contains(e.getCourseMaster().getCourseCode())
                        || e.getCourseMaster().getElectiveArea() != com.example.congraduation.abeek.domain.enums.ElectiveArea.NONE)
                .sorted(enrollmentOrder())
                .map(this::toCompletedCourseDto)
                .toList();

        return progress(
                "인증선택",
                creditSum,
                effective.getCertElectiveMinCredits(),
                "입학 " + student.getEntranceYear() + "년도",
                completed);
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

    private int[] resolveLastTakenTerm(AbeekStudent student) {
        if (student.getEnrollments() == null || student.getEnrollments().isEmpty()) {
            return new int[]{student.getGraduationAbeekYear(), 1};
        }
        return student.getEnrollments().stream()
                .map(e -> new int[]{e.getTakenYear(), e.getTakenSemester() <= 1 ? 1 : 2})
                .max(Comparator
                        .comparingInt((int[] t) -> t[0])
                        .thenComparingInt(t -> t[1]))
                .orElse(new int[]{student.getGraduationAbeekYear(), 1});
    }

    private GraduationAbeekYearResolver.GraduationTiming resolveGraduationTiming(
            AbeekStudent student,
            List<CurriculumCourse> entranceCourses,
            List<CurriculumCourse> graduationCourses
    ) {
        int[] last = resolveLastTakenTerm(student);
        String standing = resolveStandingTermInLastTerm(
                student, last[0], last[1], entranceCourses, graduationCourses);
        return graduationAbeekYearResolver.resolveFromLastTerm(last[0], last[1], standing);
    }

    private String resolveStandingTermInLastTerm(
            AbeekStudent student,
            int lastYear,
            int lastSemester,
            List<CurriculumCourse> entranceCourses,
            List<CurriculumCourse> graduationCourses
    ) {
        Map<String, String> termByCode = new HashMap<>();
        Map<String, String> termByGroup = new HashMap<>();
        Map<String, String> termByName = new HashMap<>();
        indexRecommendedTerms(entranceCourses, termByCode, termByGroup, termByName);
        indexRecommendedTerms(graduationCourses, termByCode, termByGroup, termByName);

        return student.getEnrollments().stream()
                .filter(e -> e.getTakenYear() == lastYear)
                .filter(e -> (e.getTakenSemester() <= 1 ? 1 : 2) == lastSemester)
                .map(e -> resolveRecommendedTerm(e.getCourseMaster(), termByCode, termByGroup, termByName))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(this::termOrder))
                .orElse(null);
    }

    private void indexRecommendedTerms(
            List<CurriculumCourse> courses,
            Map<String, String> termByCode,
            Map<String, String> termByGroup,
            Map<String, String> termByName
    ) {
        if (courses == null) {
            return;
        }
        for (CurriculumCourse course : courses) {
            if (course.getRecommendedTerm() == null || course.getRecommendedTerm().isBlank()) {
                continue;
            }
            CourseMaster master = course.getCourseMaster();
            // 더 늦은 학기 위치를 우선 (입학연도 4-1 vs 졸업연도 4-2 등)
            putLaterTerm(termByCode, master.getCourseCode(), course.getRecommendedTerm());
            if (master.getEquivalenceGroup() != null && !master.getEquivalenceGroup().isBlank()) {
                putLaterTerm(termByGroup, master.getEquivalenceGroup(), course.getRecommendedTerm());
            }
            putLaterTerm(termByName, normalizeCourseName(master.getName()), course.getRecommendedTerm());
        }
    }

    private void putLaterTerm(Map<String, String> map, String key, String term) {
        if (key == null || key.isBlank() || term == null || term.isBlank()) {
            return;
        }
        String existing = map.get(key);
        if (existing == null || termOrder(term) > termOrder(existing)) {
            map.put(key, term);
        }
    }

    private String resolveRecommendedTerm(
            CourseMaster master,
            Map<String, String> byCode,
            Map<String, String> byGroup,
            Map<String, String> byName
    ) {
        String byExact = byCode.get(master.getCourseCode());
        if (byExact != null) {
            return byExact;
        }
        if (master.getEquivalenceGroup() != null) {
            String byEq = byGroup.get(master.getEquivalenceGroup());
            if (byEq != null) {
                return byEq;
            }
        }
        return byName.get(normalizeCourseName(master.getName()));
    }

    private int termOrder(String term) {
        if (term == null || term.isBlank()) {
            return 0;
        }
        String[] parts = term.replaceAll("\\s+", "").split("-");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0].replaceAll("\\D", "")) * 10
                    + Integer.parseInt(parts[1].replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
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
        String normalized = normalizeCourseName(master.getName());
        if (completedNames.contains(normalized)) {
            return true;
        }
        String withoutHyphen = normalized.replace("-", "");
        if (completedNames.contains(withoutHyphen)) {
            return true;
        }
        // 고급프로그래밍입문-P ↔ 고급프로그래밍이해-P (성적표 표기 차이)
        if (isAdvancedProgrammingIntroName(normalized)) {
            return completedNames.stream().anyMatch(this::isAdvancedProgrammingIntroName);
        }
        return false;
    }

    /**
     * 기이수 행(입학 전 계절학기 포함)을 인증필수 이수 판정에 합친다.
     * enrollment 매칭이 빠져도 성적표에 있으면 이수로 본다.
     */
    private void mergeTranscriptCompletions(
            List<CompletedCourseUploadRowDto> rows,
            Set<String> completedCodes,
            Set<String> completedGroups,
            Set<String> completedNames,
            List<CurriculumCourse> entranceCourses
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (CompletedCourseUploadRowDto row : rows) {
            if (!isPassedTranscript(row)) {
                continue;
            }
            addCompletionNameVariants(completedNames, row.courseName());

            for (CurriculumCourse course : entranceCourses) {
                if (course.getRole() != CourseRole.REQUIRED && course.getRole() != CourseRole.BSM_REQUIRED) {
                    continue;
                }
                CourseMaster master = course.getCourseMaster();
                if (!namesMatchForCompletion(row.courseName(), master.getName())) {
                    continue;
                }
                completedCodes.add(master.getCourseCode());
                if (master.getEquivalenceGroup() != null && !master.getEquivalenceGroup().isBlank()) {
                    completedGroups.add(master.getEquivalenceGroup());
                }
                addCompletionNameVariants(completedNames, master.getName());
            }
        }
    }

    private void addCompletionNameVariants(Set<String> names, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return;
        }
        String normalized = normalizeCourseName(rawName);
        if (!normalized.isBlank()) {
            names.add(normalized);
            names.add(normalized.replace("-", ""));
        }
        String withoutParen = normalizeCourseName(rawName.replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            names.add(withoutParen);
            names.add(withoutParen.replace("-", ""));
        }
        if (isAdvancedProgrammingIntroName(normalized) || isAdvancedProgrammingIntroName(withoutParen)) {
            names.add(normalizeCourseName("고급프로그래밍입문-P"));
            names.add(normalizeCourseName("고급프로그래밍입문P"));
            names.add(normalizeCourseName("고급프로그래밍입문"));
            names.add(normalizeCourseName("고급프로그래밍이해-P"));
            names.add(normalizeCourseName("고급프로그래밍이해P"));
            names.add(normalizeCourseName("고급프로그래밍이해"));
        }
    }

    private boolean namesMatchForCompletion(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = normalizeCourseName(left).replace("-", "");
        String b = normalizeCourseName(right).replace("-", "");
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        if (isAdvancedProgrammingIntroName(a) && isAdvancedProgrammingIntroName(b)) {
            return true;
        }
        return a.contains(b) || b.contains(a);
    }

    private boolean isAdvancedProgrammingIntroName(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String value = normalized.replace("-", "");
        return value.contains("고급프로그래밍입문") || value.contains("고급프로그래밍이해");
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
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName
    ) {
        return student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> resolveCategory(e.getCourseMaster(), categoryByCode, categoryByGroup, categoryByName)
                        == category)
                .mapToInt(StudentEnrollment::getCredits)
                .sum();
    }

    private List<CategoryProgressDto.CompletedCourseDto> listCompletedByCategory(
            AbeekStudent student,
            CourseCategory category,
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName
    ) {
        return student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .filter(e -> resolveCategory(e.getCourseMaster(), categoryByCode, categoryByGroup, categoryByName)
                        == category)
                .sorted(enrollmentOrder())
                .map(this::toCompletedCourseDto)
                .toList();
    }

    /**
     * 전공 = 전필(REQUIRED) + 전선(ELECTIVE).
     * enrollment 매칭에 빠진 전필이 있어도 기이수 전필/전선 행을 목록에 포함한다.
     */
    private List<CategoryProgressDto.CompletedCourseDto> listMajorCompletedCourses(
            AbeekStudent student,
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName,
            List<CompletedCourseUploadRowDto> transcriptRows
    ) {
        List<CategoryProgressDto.CompletedCourseDto> fromEnrollments =
                listCompletedByCategory(student, CourseCategory.MAJOR, categoryByCode, categoryByGroup, categoryByName);

        if (transcriptRows == null || transcriptRows.isEmpty()) {
            return fromEnrollments;
        }

        Set<String> seenNames = fromEnrollments.stream()
                .map(c -> normalizeCourseName(c.getCourseName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CategoryProgressDto.CompletedCourseDto> merged = new ArrayList<>(fromEnrollments);
        for (CompletedCourseUploadRowDto row : transcriptRows) {
            if (!isPassedTranscript(row) || !isTranscriptMajorCategory(row.category())) {
                continue;
            }
            String nameKey = normalizeCourseName(row.courseName());
            if (nameKey.isBlank() || seenNames.contains(nameKey)) {
                continue;
            }
            seenNames.add(nameKey);
            merged.add(toTranscriptCompletedDto(row));
        }
        return merged;
    }

    private List<CategoryProgressDto.CompletedCourseDto> listBsmCompletedCourses(
            AbeekStudent student,
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName,
            List<CompletedCourseUploadRowDto> transcriptRows
    ) {
        List<CategoryProgressDto.CompletedCourseDto> fromEnrollments =
                listCompletedByCategory(student, CourseCategory.BSM, categoryByCode, categoryByGroup, categoryByName);

        if (transcriptRows == null || transcriptRows.isEmpty()) {
            return fromEnrollments;
        }

        Set<String> seenNames = fromEnrollments.stream()
                .map(c -> normalizeCourseName(c.getCourseName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CategoryProgressDto.CompletedCourseDto> merged = new ArrayList<>(fromEnrollments);
        for (CompletedCourseUploadRowDto row : transcriptRows) {
            if (!isPassedTranscript(row)) {
                continue;
            }
            CourseCategory resolved = resolveTranscriptRowCategory(
                    row, categoryByCode, categoryByGroup, categoryByName);
            if (resolved != CourseCategory.BSM) {
                continue;
            }
            String nameKey = normalizeCourseName(row.courseName());
            if (nameKey.isBlank() || seenNames.contains(nameKey)) {
                continue;
            }
            seenNames.add(nameKey);
            merged.add(toTranscriptCompletedDto(row));
        }
        return merged;
    }

    private CategoryProgressDto.CompletedCourseDto toTranscriptCompletedDto(CompletedCourseUploadRowDto row) {
        return CategoryProgressDto.CompletedCourseDto.builder()
                .courseCode(row.courseCode())
                .courseName(row.courseName())
                .credits(parseCreditsSafe(row.credit()))
                .designCredits(0)
                .takenYear(parseIntSafe(row.year(), null))
                .takenSemester(parseSemesterSafe(row.semester()))
                .build();
    }

    private int sumTranscriptCreditsMatchingCategory(
            List<CompletedCourseUploadRowDto> rows,
            CourseCategory target,
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName
    ) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int sum = 0;
        Set<String> counted = new HashSet<>();
        for (CompletedCourseUploadRowDto row : rows) {
            if (!isPassedTranscript(row)) {
                continue;
            }
            if (resolveTranscriptRowCategory(row, categoryByCode, categoryByGroup, categoryByName) != target) {
                continue;
            }
            String key = normalizeCourseName(row.courseName());
            if (key.isBlank() || !counted.add(key)) {
                continue;
            }
            sum += parseCreditsSafe(row.credit());
        }
        return sum;
    }

    private CourseCategory resolveTranscriptRowCategory(
            CompletedCourseUploadRowDto row,
            Map<String, CourseCategory> categoryByCode,
            Map<String, CourseCategory> categoryByGroup,
            Map<String, CourseCategory> categoryByName
    ) {
        if (row.courseCode() != null && !row.courseCode().isBlank()) {
            CourseCategory byCode = categoryByCode.get(row.courseCode());
            if (byCode != null) {
                return byCode;
            }
        }
        String normalized = normalizeCourseName(row.courseName());
        if (!normalized.isBlank()) {
            CourseCategory byName = categoryByName.get(normalized);
            if (byName != null) {
                return byName;
            }
        }
        String withoutParen = normalizeCourseName(
                row.courseName() == null ? "" : row.courseName().replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            CourseCategory byParen = categoryByName.get(withoutParen);
            if (byParen != null) {
                return byParen;
            }
        }
        // 기이수 이수구분이 전필/전선이면 전공으로 본다.
        if (isTranscriptMajorCategory(row.category())) {
            return CourseCategory.MAJOR;
        }
        return null;
    }

    private List<CompletedCourseUploadRowDto> loadTranscriptRows(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return List.of();
        }
        return appStudentRepository.findByStudentNo(studentNo.trim())
                .map(s -> {
                    try {
                        return transcriptStorageService.getLatestTranscriptRows(s.getId());
                    } catch (RuntimeException ex) {
                        return List.<CompletedCourseUploadRowDto>of();
                    }
                })
                .orElse(List.of());
    }

    private boolean isTranscriptMajorCategory(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        String value = category.replace(" ", "");
        if (value.contains("전공기초")) {
            return false;
        }
        return value.contains("전필") || value.contains("전공필수")
                || value.contains("전선") || value.contains("전공선택");
    }

    private boolean isPassedTranscript(CompletedCourseUploadRowDto course) {
        String grade = course.grade();
        if (grade == null || grade.isBlank()) {
            return true;
        }
        String normalized = grade.trim().toUpperCase(Locale.ROOT);
        return !(normalized.equals("F") || normalized.equals("NP") || normalized.equals("N")
                || normalized.equals("U") || normalized.equals("FA"));
    }

    private int parseCreditsSafe(String credit) {
        if (credit == null || credit.isBlank()) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(credit.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Integer parseIntSafe(String value, Integer defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return (int) Math.round(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Integer parseSemesterSafe(String semester) {
        if (semester == null || semester.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(semester);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private List<CategoryProgressDto.CompletedCourseDto> listAllCompleted(AbeekStudent student) {
        return student.getEnrollments().stream()
                .filter(StudentEnrollment::isPassed)
                .sorted(enrollmentOrder())
                .map(this::toCompletedCourseDto)
                .toList();
    }

    private Comparator<StudentEnrollment> enrollmentOrder() {
        return Comparator.comparingInt(StudentEnrollment::getTakenYear)
                .thenComparingInt(StudentEnrollment::getTakenSemester)
                .thenComparing(e -> e.getCourseMaster().getName());
    }

    private CategoryProgressDto.CompletedCourseDto toCompletedCourseDto(StudentEnrollment e) {
        return CategoryProgressDto.CompletedCourseDto.builder()
                .courseCode(e.getCourseMaster().getCourseCode())
                .courseName(e.getCourseMaster().getName())
                .credits(e.getCredits())
                .designCredits(e.getDesignCredits())
                .takenYear(e.getTakenYear())
                .takenSemester(e.getTakenSemester())
                .build();
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

    private CategoryProgressDto progress(
            String name,
            double earned,
            double required,
            String source,
            List<CategoryProgressDto.CompletedCourseDto> completedCourses
    ) {
        double progressPercent = required <= 0
                ? 100.0
                : Math.min(100.0, (earned / required) * 100.0);
        List<CategoryProgressDto.CompletedCourseDto> courses =
                completedCourses == null ? List.of() : completedCourses;
        return CategoryProgressDto.builder()
                .category(name)
                .earnedCredits(earned)
                .requiredCredits(required)
                .progressPercent(progressPercent)
                .satisfied(earned + 1e-9 >= required)
                .requirementSource(source)
                .completedCourseCount(courses.size())
                .completedCourses(courses)
                .build();
    }
}
