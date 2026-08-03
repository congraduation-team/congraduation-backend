package com.example.congraduation.service.graduation;

import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.dto.CurriculumCourseDto;
import com.example.congraduation.abeek.service.CurriculumQueryService;
import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.StudentMajorTrack;
import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.graduation.BalancedLiberalAreaProgressDto;
import com.example.congraduation.dto.graduation.CreditProgressDto;
import com.example.congraduation.dto.graduation.EnglishCertificationProgressDto;
import com.example.congraduation.dto.graduation.CategoryProgressDto;
import com.example.congraduation.dto.graduation.DoubleMajorGraduationRequirementProgressDto;
import com.example.congraduation.dto.graduation.GraduationWorkProgressDto;
import com.example.congraduation.dto.graduation.GraduationProgressResponseDto;
import com.example.congraduation.dto.graduation.MajorTrackProgressDto;
import com.example.congraduation.dto.graduation.MajorCreditSummaryDto;
import com.example.congraduation.dto.graduation.MajorTrackRequiredCourseProgressDto;
import com.example.congraduation.dto.graduation.MissingBalancedLiberalAreaDto;
import com.example.congraduation.dto.graduation.RemainingCommonLiberalCourseDto;
import com.example.congraduation.dto.graduation.RequirementCourseDto;
import com.example.congraduation.dto.graduation.SwCodingCertificationProgressDto;
import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.TranscriptSummaryDto;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.plan.PlannedCourseService;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import com.example.congraduation.service.transcript.TranscriptSummaryCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationProgressService {

    private static final Set<String> DOUBLE_MAJOR_REQUIRED_CATEGORIES = Set.of("복필", "복수전필", "복전필");
    private static final Set<String> DOUBLE_MAJOR_ELECTIVE_CATEGORIES = Set.of("복선", "복수전선", "복전선");
    private static final Set<String> LIBERAL_GPA_CATEGORIES = Set.of(
            "공필", "교필", "균필", "교선", "교양", "기필", "학문기초"
    );
    private static final Set<String> ARTS_GRADUATION_WORK_MAJORS = Set.of(
            "회화과", "패션디자인학과", "음악과", "체육학과", "무용과", "영화예술학과"
    );
    private static final Set<String> CREATIVE_SOFT_GRADUATION_WORK_MAJORS = Set.of(
            "디자인이노베이션전공", "만화애니메이션텍전공"
    );
    private static final List<String> GRADUATION_WORK_KEYWORDS = List.of(
            "졸업작품", "졸업시험", "졸업연주", "졸업전시", "졸업논문", "캡스톤디자인"
    );
    private static final Set<String> DEDICATED_BLOCKER_CATEGORIES = Set.of(
            "공필", "교필", "교선", "균필", "기필", "학문기초", "전기", "전공기초", "전필", "전선"
    );

    private final StudentRepository studentRepository;
    private final TranscriptStorageService transcriptStorageService;
    private final TranscriptSummaryCalculator transcriptSummaryCalculator;
    private final PlannedCourseService plannedCourseService;
    private final DepartmentCurriculumPolicyService policyService;
    private final CurriculumQueryService curriculumQueryService;
    private final BalancedLiberalCoursePolicyService balancedLiberalCoursePolicyService;
    private final DoubleMajorRequiredCoursePolicyService doubleMajorRequiredCoursePolicyService;
    private final DoubleMajorGraduationRequirementService doubleMajorGraduationRequirementService;
    private final MinorTrackProgressService minorTrackProgressService;
    private final SwCodingCertificationService swCodingCertificationService;
    private final EnglishCertificationService englishCertificationService;

    public GraduationProgressService(
            StudentRepository studentRepository,
            TranscriptStorageService transcriptStorageService,
            TranscriptSummaryCalculator transcriptSummaryCalculator,
            PlannedCourseService plannedCourseService,
            DepartmentCurriculumPolicyService policyService,
            CurriculumQueryService curriculumQueryService,
            BalancedLiberalCoursePolicyService balancedLiberalCoursePolicyService,
            DoubleMajorRequiredCoursePolicyService doubleMajorRequiredCoursePolicyService,
            DoubleMajorGraduationRequirementService doubleMajorGraduationRequirementService,
            MinorTrackProgressService minorTrackProgressService,
            SwCodingCertificationService swCodingCertificationService,
            EnglishCertificationService englishCertificationService
    ) {
        this.studentRepository = studentRepository;
        this.transcriptStorageService = transcriptStorageService;
        this.transcriptSummaryCalculator = transcriptSummaryCalculator;
        this.plannedCourseService = plannedCourseService;
        this.policyService = policyService;
        this.curriculumQueryService = curriculumQueryService;
        this.balancedLiberalCoursePolicyService = balancedLiberalCoursePolicyService;
        this.doubleMajorRequiredCoursePolicyService = doubleMajorRequiredCoursePolicyService;
        this.doubleMajorGraduationRequirementService = doubleMajorGraduationRequirementService;
        this.minorTrackProgressService = minorTrackProgressService;
        this.swCodingCertificationService = swCodingCertificationService;
        this.englishCertificationService = englishCertificationService;
    }

    @Transactional(readOnly = true)
    public GraduationProgressResponseDto evaluate(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.getLatestTranscriptRows(studentId);
        PlannedCourseListResponseDto plannedCourses = plannedCourseService.getPlannedCourses(studentId);
        List<CompletedCourseUploadRowDto> projectedCourses = plannedCourseService.getProjectedRows(studentId);
        DepartmentCurriculumPolicy policy = policyService.resolve(student);
        List<CompletedCourseUploadRowDto> normalizedCourses = normalizeCoursesForPolicy(courses, policy);
        List<CompletedCourseUploadRowDto> normalizedProjectedCourses = normalizeCoursesForPolicy(projectedCourses, policy);
        GraduationProgressResponseDto simulation = buildProgressResponse(
                student,
                policy,
                plannedCourses,
                normalizedCourses,
                normalizedProjectedCourses,
                null
        );
        return buildProgressResponse(
                student,
                policy,
                plannedCourses,
                normalizedCourses,
                List.of(),
                simulation
        );
    }

    private GraduationProgressResponseDto buildProgressResponse(
            Student student,
            DepartmentCurriculumPolicy policy,
            PlannedCourseListResponseDto plannedCourses,
            List<CompletedCourseUploadRowDto> normalizedCourses,
            List<CompletedCourseUploadRowDto> additionalCourses,
            GraduationProgressResponseDto simulation
    ) {
        List<CompletedCourseUploadRowDto> evaluationCourses = new ArrayList<>(normalizedCourses);
        evaluationCourses.addAll(additionalCourses);
        evaluationCourses = transcriptSummaryCalculator.resolveRetakenCourses(evaluationCourses);
        List<CompletedCourseUploadRowDto> completedCourses = evaluationCourses.stream()
                .filter(this::isPassedForCompletion)
                .toList();
        TranscriptSummaryDto transcriptSummary = transcriptSummaryCalculator.summarize(completedCourses);
        BalancedLiberalEvaluation balancedLiberalEvaluation = evaluateBalancedLiberal(student, completedCourses);
        MajorTrackCreditPolicy primaryMajorPolicy = resolvePrimaryMajorPolicy(student);
        Map<String, Integer> categoryRequirements = resolveCategoryRequirements(policy, primaryMajorPolicy);
        BigDecimal majorFoundationEarnedCredits =
                calculateMajorFoundationCredits(student, policy, transcriptSummary.categorySummaries(), completedCourses);
        List<CategoryCourseDto> majorFoundationCourses =
                extractMajorFoundationCourses(student, policy, completedCourses);
        List<MajorTrackProgressDto> majorTracks =
                buildMajorTrackProgresses(student, transcriptSummary.categorySummaries(), completedCourses);
        GraduationWorkProgressDto graduationWork = buildGraduationWorkProgress(student, completedCourses);
        EnglishCertificationProgressDto englishCertification =
                englishCertificationService.evaluate(student, completedCourses);
        SwCodingCertificationProgressDto swCodingCertification =
                swCodingCertificationService.evaluate(student, completedCourses);
        CreditProgressDto totalCreditsProgress = buildCreditProgress(transcriptSummary.totalCredits(), policy.graduationCredits());
        CategoryProgressDto commonLiberalProgress =
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.commonLiberalCredits(), "공필", "교필");
        List<CategoryCourseDto> commonLiberalCourses =
                extractCategoryCourses(transcriptSummary.categorySummaries(), "공필", "교필");
        List<RemainingCommonLiberalCourseDto> remainingCommonLiberalRequiredCourses =
                extractRemainingCommonLiberalRequiredCourses(student, completedCourses);
        List<RequirementCourseDto> remainingMajorRequiredCourses =
                extractRemainingMajorRequiredCourses(student, completedCourses);
        List<RequirementCourseDto> remainingMajorElectiveCourses =
                extractRemainingMajorElectiveCourses(student, completedCourses);
        CategoryProgressDto electiveLiberalProgress =
                buildCategoryProgress(transcriptSummary.categorySummaries(), 0, "교선");
        CategoryProgressDto academicFoundationProgress =
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.academicFoundationCredits(), "기필", "학문기초");
        CategoryProgressDto majorFoundationProgress = buildCategoryProgress(
                majorFoundationEarnedCredits,
                isDoubleMajorStudent(student) ? null : policy.majorFoundationCredits()
        );
        MajorCreditSummaryDto majorCreditSummary =
                buildMajorCreditSummary(transcriptSummary, policy, primaryMajorPolicy, majorFoundationEarnedCredits);
        List<CategorySummaryDto> categorySummaries =
                applyCategoryRequirements(transcriptSummary.categorySummaries(), categoryRequirements);
        categorySummaries = applyMajorFoundationSummary(categorySummaries, policy, majorFoundationEarnedCredits, majorFoundationCourses);
        List<String> missingBalancedLiberalAreas =
                resolveMissingBalancedLiberalAreas(student, balancedLiberalEvaluation.areaProgresses());
        List<MissingBalancedLiberalAreaDto> missingBalancedLiberalAreaDetails =
                resolveMissingBalancedLiberalAreaDetails(student, missingBalancedLiberalAreas);
        List<String> graduationBlockers = buildGraduationBlockers(
                totalCreditsProgress,
                commonLiberalProgress,
                electiveLiberalProgress,
                balancedLiberalEvaluation.progress(),
                academicFoundationProgress,
                majorFoundationProgress,
                majorCreditSummary,
                majorTracks,
                graduationWork,
                categorySummaries
        );
        boolean graduationEligible = graduationBlockers.isEmpty();

        return new GraduationProgressResponseDto(
                student.getId(),
                student.getAdmissionYear(),
                student.getMajor(),
                student.getMajorType(),
                student.getSecondaryMajor(),
                graduationEligible,
                graduationBlockers,
                simulation == null ? graduationBlockers : simulation.graduationBlockers(),
                majorTracks,
                graduationWork,
                englishCertification,
                swCodingCertification,
                totalCreditsProgress,
                commonLiberalProgress,
                commonLiberalCourses,
                remainingCommonLiberalRequiredCourses,
                remainingMajorRequiredCourses,
                remainingMajorElectiveCourses,
                electiveLiberalProgress,
                balancedLiberalEvaluation.progress(),
                missingBalancedLiberalAreas,
                missingBalancedLiberalAreaDetails,
                balancedLiberalEvaluation.requiredAreaCount(),
                balancedLiberalEvaluation.completedAreaCount(),
                balancedLiberalEvaluation.areaProgresses(),
                academicFoundationProgress,
                majorFoundationProgress,
                calculateAverageGradePoint(evaluationCourses),
                calculateMajorGradePoint(evaluationCourses),
                calculateLiberalGradePoint(evaluationCourses),
                plannedCourses.totalPlannedCredits(),
                plannedCourses.semesters(),
                majorCreditSummary,
                categorySummaries,
                simulation
        );
    }

    private List<String> buildGraduationBlockers(
            CreditProgressDto totalCreditsProgress,
            CategoryProgressDto commonLiberalProgress,
            CategoryProgressDto electiveLiberalProgress,
            CategoryProgressDto balancedLiberalProgress,
            CategoryProgressDto academicFoundationProgress,
            CategoryProgressDto majorFoundationProgress,
            MajorCreditSummaryDto majorCreditSummary,
            List<MajorTrackProgressDto> majorTracks,
            GraduationWorkProgressDto graduationWork,
            List<CategorySummaryDto> categorySummaries
    ) {
        Set<String> blockers = new LinkedHashSet<>();

        if (!totalCreditsProgress.satisfied()) {
            blockers.add("총 이수학점이 부족합니다.");
        }
        if (!commonLiberalProgress.satisfied()) {
            blockers.add("공통교양 요건을 충족하지 못했습니다.");
        }
        if (!electiveLiberalProgress.satisfied()) {
            blockers.add("교양선택 요건을 충족하지 못했습니다.");
        }
        if (!balancedLiberalProgress.satisfied()) {
            blockers.add("균형교양 요건을 충족하지 못했습니다.");
        }
        if (!academicFoundationProgress.satisfied()) {
            blockers.add("학문기초 요건을 충족하지 못했습니다.");
        }
        if (majorFoundationProgress.requiredCredits() != null && !majorFoundationProgress.satisfied()) {
            blockers.add("전공기초 요건을 충족하지 못했습니다.");
        }
        if (!majorCreditSummary.majorCreditsSatisfied()) {
            blockers.add("전공 총 학점이 부족합니다.");
        }
        if (!majorCreditSummary.majorRequiredSatisfied()) {
            blockers.add("전공필수 학점이 부족합니다.");
        }
        if (!majorCreditSummary.majorElectiveSatisfied()) {
            blockers.add("전공선택 학점이 부족합니다.");
        }
        if (graduationWork.required() && !graduationWork.satisfied()) {
            blockers.add("졸업작품(시험) 요건을 충족하지 못했습니다.");
        }

        for (MajorTrackProgressDto majorTrack : majorTracks) {
            if (isDoubleMajorType(majorTrack.trackType()) && "IN_PROGRESS".equalsIgnoreCase(majorTrack.status())) {
                blockers.add(majorTrack.department() + " 복수전공 요건을 충족하지 못했습니다.");
            }
        }

        for (CategorySummaryDto categorySummary : categorySummaries) {
            if (categorySummary.requiredCredits() == null || categorySummary.requiredCredits().isBlank()) {
                continue;
            }
            if (DEDICATED_BLOCKER_CATEGORIES.contains(categorySummary.category())) {
                continue;
            }
            if (!categorySummary.satisfied()) {
                blockers.add(categorySummary.category() + " 이수구분 요건을 충족하지 못했습니다.");
            }
        }

        return new ArrayList<>(blockers);
    }

    private BalancedLiberalEvaluation evaluateBalancedLiberal(
            Student student,
            List<CompletedCourseUploadRowDto> courses
    ) {
        BalancedLiberalCoursePolicyService.BalancedLiberalRequirement requirement =
                balancedLiberalCoursePolicyService.resolveRequirement(student.getAdmissionYear());

        if (requirement.requiredCredits() <= 0 || requirement.requiredAreaCount() <= 0) {
            return new BalancedLiberalEvaluation(
                    new CategoryProgressDto("0", null, false, null),
                    0,
                    0,
                    List.of()
            );
        }

        Map<String, AreaAccumulator> areaMap = new LinkedHashMap<>();
        Set<String> countedCourseCodes = new LinkedHashSet<>();

        for (CompletedCourseUploadRowDto course : courses) {
            if (!balancedLiberalCoursePolicyService.isEligibleAcademicYear(student.getAdmissionYear(), course)) {
                continue;
            }

            String area = balancedLiberalCoursePolicyService.resolveArea(student, course);
            if (area == null) {
                continue;
            }

            String courseCode = course.courseCode() == null ? "" : course.courseCode().trim();
            if (courseCode.isBlank() || !countedCourseCodes.add(courseCode)) {
                continue;
            }

            AreaAccumulator accumulator = areaMap.computeIfAbsent(area, key -> new AreaAccumulator());
            BigDecimal credit = toDecimal(course.credit());
            accumulator.earnedCredits = accumulator.earnedCredits.add(credit);
            accumulator.courses.add(new CategoryCourseDto(
                    course.courseCode(),
                    course.courseName(),
                    course.credit()
            ));
        }

        List<BalancedLiberalAreaProgressDto> areaProgresses = areaMap.entrySet().stream()
                .map(entry -> new BalancedLiberalAreaProgressDto(
                        entry.getKey(),
                        formatDecimal(entry.getValue().earnedCredits),
                        entry.getValue().earnedCredits.compareTo(BigDecimal.valueOf(3)) >= 0,
                        List.copyOf(entry.getValue().courses)
                ))
                .toList();

        BigDecimal totalEarned = areaMap.values().stream()
                .map(accumulator -> accumulator.earnedCredits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long completedAreaCount = areaProgresses.stream()
                .filter(BalancedLiberalAreaProgressDto::satisfied)
                .count();

        boolean satisfied = totalEarned.compareTo(BigDecimal.valueOf(requirement.requiredCredits())) >= 0
                && completedAreaCount >= requirement.requiredAreaCount();

        return new BalancedLiberalEvaluation(
                new CategoryProgressDto(
                        formatDecimal(totalEarned),
                        String.valueOf(requirement.requiredCredits()),
                        satisfied,
                        toPercentString(totalEarned, requirement.requiredCredits())
                ),
                requirement.requiredAreaCount(),
                (int) completedAreaCount,
                areaProgresses
        );
    }

    private List<MajorTrackProgressDto> buildMajorTrackProgresses(
            Student student,
            List<CategorySummaryDto> categorySummaries,
            List<CompletedCourseUploadRowDto> courses
    ) {
        List<StudentMajorTrack> tracks = resolveAdditionalTracks(student);
        List<MajorTrackProgressDto> progresses = new ArrayList<>();
        for (StudentMajorTrack track : tracks) {
            if (isDoubleMajorType(track.getTrackType())) {
                progresses.add(buildDoubleMajorProgress(student, track, categorySummaries, courses));
                continue;
            }
            if (track.getTrackType() == com.example.congraduation.domain.MajorType.MINOR) {
                progresses.add(minorTrackProgressService.evaluate(track, categorySummaries));
            }
        }
        return List.copyOf(progresses);
    }

    private MajorTrackProgressDto buildDoubleMajorProgress(
            Student student,
            StudentMajorTrack track,
            List<CategorySummaryDto> categorySummaries,
            List<CompletedCourseUploadRowDto> courses
    ) {
        MajorTrackCreditPolicy policy = resolveDoubleMajorPolicy(student, track);
        BigDecimal required = creditOf(categorySummaries, "복필", "복수전필", "복전필");
        BigDecimal elective = creditOf(categorySummaries, "복선", "복수전선", "복전선");
        BigDecimal total = required.add(elective);
        DoubleMajorRequiredCoursePolicyService.RequiredCourseEvaluation requiredCourseEvaluation =
                doubleMajorRequiredCoursePolicyService.evaluate(track.getDepartmentCode(), courses);
        DoubleMajorGraduationRequirementProgressDto graduationRequirement =
                doubleMajorGraduationRequirementService.evaluate(student, track, courses);
        MajorTrackRequiredCourseProgressDto requiredCourseProgress = new MajorTrackRequiredCourseProgressDto(
                requiredCourseEvaluation.policyApplied(),
                requiredCourseEvaluation.policyApplied() ? requiredCourseEvaluation.requiredCourseCount() : null,
                requiredCourseEvaluation.policyApplied() ? requiredCourseEvaluation.completedCourseCount() : null,
                requiredCourseEvaluation.satisfied(),
                requiredCourseEvaluation.completedCourses(),
                requiredCourseEvaluation.missingCourses()
        );
        boolean creditSatisfied = isSatisfied(required, policy.requiredCredits()) && isSatisfied(elective, policy.electiveCredits());
        boolean trackSatisfied = creditSatisfied
                && requiredCourseEvaluation.satisfied()
                && isDoubleMajorGraduationRequirementSatisfied(graduationRequirement);

        return new MajorTrackProgressDto(
                track.getTrackType(),
                track.getDepartmentCode(),
                new CreditProgressDto(
                        formatDecimal(total),
                        String.valueOf(policy.totalCredits()),
                        isSatisfied(total, policy.totalCredits()),
                        toPercentString(total, policy.totalCredits())
                ),
                new CreditProgressDto(
                        formatDecimal(required),
                        String.valueOf(policy.requiredCredits()),
                        isSatisfied(required, policy.requiredCredits()),
                        toPercentString(required, policy.requiredCredits())
                ),
                new CreditProgressDto(
                        formatDecimal(elective),
                        String.valueOf(policy.electiveCredits()),
                        isSatisfied(elective, policy.electiveCredits()),
                        toPercentString(elective, policy.electiveCredits())
                ),
                requiredCourseProgress,
                graduationRequirement,
                "복필/복선",
                resolveDoubleMajorTrackStatus(trackSatisfied, graduationRequirement),
                graduationRequirement.detail()
        );
    }

    private MajorTrackCreditPolicy resolveDoubleMajorPolicy(Student student, StudentMajorTrack track) {
        String secondary = normalizeMajor(track.getDepartmentCode());
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();

        if ("건축학과".equals(secondary)) {
            return new MajorTrackCreditPolicy(99, 24);
        }

        if ("법학전공".equals(secondary)) {
            if (admissionYear >= 2024) {
                return new MajorTrackCreditPolicy(21, 18);
            }
            return new MajorTrackCreditPolicy(24, 15);
        }

        return new MajorTrackCreditPolicy(15, 24);
    }

    private MajorTrackCreditPolicy resolvePrimaryMajorPolicy(Student student) {
        if (!isDoubleMajorStudent(student)) {
            return null;
        }

        String primary = normalizeMajor(student.getMajor());
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();

        if ("건축학과".equals(primary)) {
            return new MajorTrackCreditPolicy(99, 24);
        }

        if ("법학전공".equals(primary)) {
            if (admissionYear >= 2024) {
                return new MajorTrackCreditPolicy(21, 18);
            }
            return new MajorTrackCreditPolicy(24, 15);
        }

        if ("항공시스템공학전공".equals(primary)) {
            return new MajorTrackCreditPolicy(38, 11);
        }

        if ("국방AI융합시스템공학과".equals(primary) || "국방시스템공학과".equals(primary)) {
            return new MajorTrackCreditPolicy(39, 6);
        }

        return new MajorTrackCreditPolicy(15, 24);
    }

    private Map<String, Integer> resolveCategoryRequirements(
            DepartmentCurriculumPolicy policy,
            MajorTrackCreditPolicy primaryMajorPolicy
    ) {
        Map<String, Integer> requirements = new LinkedHashMap<>(policy.categoryRequiredCredits());
        if (primaryMajorPolicy == null) {
            return requirements;
        }

        requirements.put("전필", primaryMajorPolicy.requiredCredits());
        requirements.put("전선", primaryMajorPolicy.electiveCredits());
        requirements.remove("전기");
        requirements.remove("전공기초");
        return requirements;
    }

    private GraduationWorkProgressDto buildGraduationWorkProgress(
            Student student,
            List<CompletedCourseUploadRowDto> courses
    ) {
        String major = normalizeMajor(student.getMajor());
        boolean artsRequired = ARTS_GRADUATION_WORK_MAJORS.contains(major);
        boolean creativeSoftRequired = CREATIVE_SOFT_GRADUATION_WORK_MAJORS.contains(major);

        if (!artsRequired && !creativeSoftRequired) {
            return new GraduationWorkProgressDto(
                    false,
                    false,
                    "NOT_APPLICABLE",
                    "NOT_REQUIRED",
                    "해당 학과는 단일전공 졸업작품 대상이 아닙니다."
            );
        }

        String matchedCourse = courses.stream()
                .map(CompletedCourseUploadRowDto::courseName)
                .filter(courseName -> courseName != null && containsGraduationWorkKeyword(courseName))
                .findFirst()
                .orElse(null);

        boolean satisfied = matchedCourse != null;
        return new GraduationWorkProgressDto(
                true,
                satisfied,
                satisfied ? "COMPLETED" : "IN_PROGRESS",
                artsRequired ? "GRADUATION_CERT_SUBSTITUTE" : "MAJOR_REQUIRED",
                satisfied ? matchedCourse : "졸업작품(시험) 이수 내역을 확인하지 못했습니다."
        );
    }

    private List<CompletedCourseUploadRowDto> normalizeCoursesForPolicy(
            List<CompletedCourseUploadRowDto> courses,
            DepartmentCurriculumPolicy policy
    ) {
        if (hasMajorFoundationRequirement(policy)) {
            return courses;
        }

        return courses.stream()
                .map(this::mapLegacyMajorFoundationToElective)
                .toList();
    }

    private CompletedCourseUploadRowDto mapLegacyMajorFoundationToElective(CompletedCourseUploadRowDto course) {
        if (!hasCategory(course.category(), "전기", "전공기초")) {
            return course;
        }

        return new CompletedCourseUploadRowDto(
                course.year(),
                course.semester(),
                course.courseCode(),
                course.courseName(),
                "전선",
                course.credit(),
                course.evaluationMethod(),
                course.grade(),
                course.gradePoint()
        );
    }

    private MajorCreditSummaryDto buildMajorCreditSummary(
            TranscriptSummaryDto transcriptSummary,
            DepartmentCurriculumPolicy policy,
            MajorTrackCreditPolicy primaryMajorPolicy,
            BigDecimal majorFoundationEarnedCredits
    ) {
        BigDecimal majorRequired = creditOf(transcriptSummary.categorySummaries(), "전필");
        BigDecimal majorElective = creditOf(transcriptSummary.categorySummaries(), "전선");
        BigDecimal majorTotal = majorRequired.add(majorElective);
        int requiredMajorTotal = primaryMajorPolicy == null
                ? policy.majorTotalCredits()
                : primaryMajorPolicy.totalCredits();
        int requiredMajorRequired = primaryMajorPolicy == null
                ? policy.majorRequiredCredits()
                : primaryMajorPolicy.requiredCredits();
        int requiredMajorElective = primaryMajorPolicy == null
                ? policy.majorElectiveCredits()
                : primaryMajorPolicy.electiveCredits();

        return new MajorCreditSummaryDto(
                formatDecimal(majorTotal),
                formatRequired(requiredMajorTotal),
                isSatisfied(majorTotal, requiredMajorTotal),
                toPercentString(majorTotal, requiredMajorTotal),
                formatDecimal(majorRequired),
                formatRequired(requiredMajorRequired),
                isSatisfied(majorRequired, requiredMajorRequired),
                toPercentString(majorRequired, requiredMajorRequired),
                formatDecimal(majorElective),
                formatRequired(requiredMajorElective),
                isSatisfied(majorElective, requiredMajorElective),
                toPercentString(majorElective, requiredMajorElective),
                formatDecimal(majorFoundationEarnedCredits),
                courseCountOf(transcriptSummary.categorySummaries(), "전필"),
                courseCountOf(transcriptSummary.categorySummaries(), "전선"),
                primaryMajorPolicy == null
                        ? courseCountOf(transcriptSummary.categorySummaries(), "전필", "전선", "전기", "전공기초")
                        : courseCountOf(transcriptSummary.categorySummaries(), "전필", "전선")
        );
    }

    private List<CategorySummaryDto> applyCategoryRequirements(
            List<CategorySummaryDto> categorySummaries,
            Map<String, Integer> requirements
    ) {
        Map<String, CategorySummaryDto> merged = new LinkedHashMap<>();

        for (CategorySummaryDto summary : categorySummaries) {
            String canonicalCategory = canonicalRequirementCategory(summary.category(), requirements);
            int required = requirements.getOrDefault(canonicalCategory, 0);
            BigDecimal earned = new BigDecimal(summary.earnedCredits());
            CategorySummaryDto existing = merged.get(canonicalCategory);

            if (existing == null) {
                merged.put(canonicalCategory, new CategorySummaryDto(
                        canonicalCategory,
                        summary.earnedCredits(),
                        formatRequired(required),
                        isSatisfied(earned, required),
                        toPercentString(earned, required),
                        summary.courses()
                ));
                continue;
            }

            BigDecimal mergedEarned = new BigDecimal(existing.earnedCredits()).add(earned);
            List<CategoryCourseDto> mergedCourses = new ArrayList<>();
            if (existing.courses() != null) {
                mergedCourses.addAll(existing.courses());
            }
            if (summary.courses() != null) {
                mergedCourses.addAll(summary.courses());
            }

            merged.put(canonicalCategory, new CategorySummaryDto(
                    canonicalCategory,
                    formatDecimal(mergedEarned),
                    formatRequired(required),
                    isSatisfied(mergedEarned, required),
                    toPercentString(mergedEarned, required),
                    mergedCourses
            ));
        }

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
                continue;
            }
            merged.put(entry.getKey(), new CategorySummaryDto(
                    entry.getKey(),
                    "0",
                    String.valueOf(entry.getValue()),
                    false,
                    "0.00",
                    new ArrayList<>()
            ));
        }

        return new ArrayList<>(merged.values());
    }

    private String canonicalRequirementCategory(String category, Map<String, Integer> requirements) {
        if ("교필".equals(category) && requirements.containsKey("공필")) {
            return "공필";
        }
        if ("학문기초".equals(category) && requirements.containsKey("기필")) {
            return "기필";
        }
        if ("전공기초".equals(category) && requirements.containsKey("전기")) {
            return "전기";
        }
        return category;
    }

    private BigDecimal creditOf(List<CategorySummaryDto> summaries, String... categories) {
        return summaries.stream()
                .filter(summary -> Arrays.stream(categories).anyMatch(category -> summary.category().equals(category)))
                .map(summary -> new BigDecimal(summary.earnedCredits()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int courseCountOf(List<CategorySummaryDto> summaries, String... categories) {
        Set<String> wanted = Set.of(categories);
        return summaries.stream()
                .filter(summary -> wanted.contains(summary.category()))
                .mapToInt(summary -> summary.courses() == null ? 0 : summary.courses().size())
                .sum();
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatRequired(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return String.valueOf(value);
    }

    private CreditProgressDto buildCreditProgress(String earnedCredits, int requiredCredits) {
        BigDecimal earned = new BigDecimal(earnedCredits);
        return new CreditProgressDto(
                earnedCredits,
                String.valueOf(requiredCredits),
                isSatisfied(earned, requiredCredits),
                toPercentString(earned, requiredCredits)
        );
    }

    private CategoryProgressDto buildCategoryProgress(List<CategorySummaryDto> summaries, Integer requiredCredits, String... categories) {
        BigDecimal earned = creditOf(summaries, categories);
        return buildCategoryProgress(earned, requiredCredits);
    }

    private CategoryProgressDto buildCategoryProgress(BigDecimal earned, Integer requiredCredits) {
        return new CategoryProgressDto(
                formatDecimal(earned),
                formatRequired(requiredCredits),
                isSatisfied(earned, requiredCredits),
                toPercentString(earned, requiredCredits)
        );
    }

    private boolean hasMajorFoundationRequirement(DepartmentCurriculumPolicy policy) {
        return policy.majorFoundationCredits() != null && policy.majorFoundationCredits() > 0;
    }

    private List<CategorySummaryDto> applyMajorFoundationSummary(
            List<CategorySummaryDto> categorySummaries,
            DepartmentCurriculumPolicy policy,
            BigDecimal majorFoundationEarnedCredits,
            List<CategoryCourseDto> majorFoundationCourses
    ) {
        if (!hasMajorFoundationRequirement(policy)) {
            return categorySummaries;
        }

        List<CategorySummaryDto> updated = new ArrayList<>();
        boolean replaced = false;
        for (CategorySummaryDto summary : categorySummaries) {
            if (!"전기".equals(summary.category())) {
                updated.add(summary);
                continue;
            }

            updated.add(new CategorySummaryDto(
                    "전기",
                    formatDecimal(majorFoundationEarnedCredits),
                    formatRequired(policy.majorFoundationCredits()),
                    isSatisfied(majorFoundationEarnedCredits, policy.majorFoundationCredits()),
                    toPercentString(majorFoundationEarnedCredits, policy.majorFoundationCredits()),
                    majorFoundationCourses
            ));
            replaced = true;
        }

        if (!replaced) {
            updated.add(new CategorySummaryDto(
                    "전기",
                    formatDecimal(majorFoundationEarnedCredits),
                    formatRequired(policy.majorFoundationCredits()),
                    isSatisfied(majorFoundationEarnedCredits, policy.majorFoundationCredits()),
                    toPercentString(majorFoundationEarnedCredits, policy.majorFoundationCredits()),
                    majorFoundationCourses
            ));
        }

        return List.copyOf(updated);
    }

    private List<CategoryCourseDto> extractCategoryCourses(List<CategorySummaryDto> summaries, String... categories) {
        List<CategoryCourseDto> courses = new ArrayList<>();
        for (CategorySummaryDto summary : summaries) {
            if (Arrays.stream(categories).noneMatch(category -> summary.category().equals(category))) {
                continue;
            }
            courses.addAll(summary.courses());
        }
        return List.copyOf(courses);
    }

    private List<RequirementCourseDto> extractRemainingMajorRequiredCourses(
            Student student,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        return extractRemainingMajorCourses(student, completedCourses, CourseRole.REQUIRED);
    }

    private List<RequirementCourseDto> extractRemainingMajorElectiveCourses(
            Student student,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        return extractRemainingMajorCourses(student, completedCourses, CourseRole.ELECTIVE);
    }

    private List<RequirementCourseDto> extractRemainingMajorCourses(
            Student student,
            List<CompletedCourseUploadRowDto> completedCourses,
            CourseRole targetRole
    ) {
        Integer admissionYear = student.getAdmissionYear();
        String departmentCode = resolveCurriculumDepartmentCode(student.getMajor());
        if (admissionYear == null || departmentCode == null || departmentCode.isBlank()) {
            return List.of();
        }

        List<CurriculumCourseDto> curriculumCourses;
        try {
            curriculumCourses = curriculumQueryService.getCurriculum(departmentCode, admissionYear);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }

        Set<String> completedCourseNames = completedCourses.stream()
                .map(CompletedCourseUploadRowDto::courseName)
                .map(this::normalizeCourseName)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return curriculumCourses.stream()
                .filter(course -> course.getRole() == targetRole)
                .filter(course -> course.getCategory() == CourseCategory.MAJOR)
                .filter(course -> !course.isNewlyIntroducedRequired())
                .filter(course -> !completedCourseNames.contains(normalizeCourseName(course.getCourseName())))
                .sorted(Comparator
                        .comparing((CurriculumCourseDto course) -> defaultString(course.getRecommendedTerm()))
                        .thenComparing(CurriculumCourseDto::getCourseName))
                .map(course -> new RequirementCourseDto(
                        course.getCourseCode(),
                        course.getCourseName(),
                        String.valueOf(course.getCredits()),
                        course.getRecommendedTerm()
                ))
                .toList();
    }

    private List<RemainingCommonLiberalCourseDto> extractRemainingCommonLiberalRequiredCourses(
            Student student,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        List<CategoryCourseDto> requiredCourses =
                balancedLiberalCoursePolicyService.requiredCommonLiberalCourses(student.getAdmissionYear());
        if (requiredCourses.isEmpty()) {
            return List.of();
        }

        return requiredCourses.stream()
                .filter(course -> !hasCompletedEquivalentCommonLiberalCourse(course, completedCourses))
                .map(course -> new RemainingCommonLiberalCourseDto(course, List.of()))
                .toList();
    }

    private List<String> resolveMissingBalancedLiberalAreas(
            Student student,
            List<BalancedLiberalAreaProgressDto> areaProgresses
    ) {
        List<String> availableAreas = balancedLiberalCoursePolicyService.availableAreas(student);
        if (availableAreas.isEmpty()) {
            return List.of();
        }

        Set<String> completedAreas = areaProgresses.stream()
                .filter(BalancedLiberalAreaProgressDto::satisfied)
                .map(BalancedLiberalAreaProgressDto::area)
                .collect(java.util.stream.Collectors.toSet());

        return availableAreas.stream()
                .filter(area -> !completedAreas.contains(area))
                .toList();
    }

    private List<MissingBalancedLiberalAreaDto> resolveMissingBalancedLiberalAreaDetails(
            Student student,
            List<String> missingAreas
    ) {
        if (missingAreas.isEmpty()) {
            return List.of();
        }

        Map<String, List<CategoryCourseDto>> catalog =
                balancedLiberalCoursePolicyService.balancedLiberalAreaCatalog(student);

        return missingAreas.stream()
                .map(area -> new MissingBalancedLiberalAreaDto(
                        area,
                        catalog.getOrDefault(area, List.of())
                ))
                .toList();
    }

    private boolean hasCompletedEquivalentCommonLiberalCourse(
            CategoryCourseDto requiredCourse,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        Set<String> equivalentNames =
                balancedLiberalCoursePolicyService.commonLiberalEquivalentNames(requiredCourse.courseCode());
        if (equivalentNames.isEmpty()) {
            equivalentNames = Set.of(requiredCourse.courseName());
        }

        Set<String> normalizedEquivalentNames = equivalentNames.stream()
                .map(this::normalizeCourseName)
                .collect(java.util.stream.Collectors.toSet());

        return completedCourses.stream()
                .map(CompletedCourseUploadRowDto::courseName)
                .map(this::normalizeCourseName)
                .anyMatch(normalizedEquivalentNames::contains);
    }

    private BigDecimal calculateMajorFoundationCredits(
            Student student,
            DepartmentCurriculumPolicy policy,
            List<CategorySummaryDto> summaries,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        BigDecimal earned = creditOf(summaries, "전기", "전공기초");
        if (!hasMajorFoundationRequirement(policy)) {
            return earned;
        }

        DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule =
                policyService.resolveMajorFoundationCourseRule(student);
        if (rule.requiredCourseNames().isEmpty() && rule.optionalCourseNames().isEmpty()) {
            return earned;
        }

        Set<String> countedCourseKeys = new LinkedHashSet<>();
        for (CompletedCourseUploadRowDto course : completedCourses) {
            if (!hasCategory(course.category(), "전기", "전공기초")) {
                continue;
            }
            countedCourseKeys.add(uniqueCourseKey(course));
        }

        BigDecimal additional = extractMatchedMajorFoundationCourses(rule, completedCourses, countedCourseKeys).stream()
                .map(CategoryCourseDto::credit)
                .map(this::toDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return earned.add(additional);
    }

    private List<CategoryCourseDto> extractMajorFoundationCourses(
            Student student,
            DepartmentCurriculumPolicy policy,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        if (!hasMajorFoundationRequirement(policy)) {
            return List.of();
        }

        Map<String, CategoryCourseDto> courses = new LinkedHashMap<>();

        for (CompletedCourseUploadRowDto course : completedCourses) {
            if (!hasCategory(course.category(), "전기", "전공기초")) {
                continue;
            }

            courses.putIfAbsent(
                    uniqueCourseKey(course),
                    new CategoryCourseDto(course.courseCode(), course.courseName(), course.credit())
            );
        }

        DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule =
                policyService.resolveMajorFoundationCourseRule(student);
        for (CategoryCourseDto course : extractMatchedMajorFoundationCourses(rule, completedCourses, courses.keySet())) {
            courses.putIfAbsent(uniqueCourseKey(course.courseCode(), course.courseName()), course);
        }

        return List.copyOf(courses.values());
    }

    private List<CategoryCourseDto> extractMatchedMajorFoundationCourses(
            DepartmentCurriculumPolicyService.MajorFoundationCourseRule rule,
            List<CompletedCourseUploadRowDto> completedCourses,
            Set<String> excludedCourseKeys
    ) {
        if ((rule.requiredCourseNames().isEmpty() && rule.optionalCourseNames().isEmpty()) || completedCourses.isEmpty()) {
            return List.of();
        }

        Set<String> requiredNames = normalizeMajorFoundationRuleNames(rule.requiredCourseNames());
        Set<String> optionalNames = normalizeMajorFoundationRuleNames(rule.optionalCourseNames());
        Map<String, CategoryCourseDto> requiredCourses = new LinkedHashMap<>();
        List<CompletedCourseUploadRowDto> optionalCandidates = new ArrayList<>();

        for (CompletedCourseUploadRowDto course : completedCourses) {
            String normalizedName = normalizeCourseName(course.courseName());
            String courseKey = uniqueCourseKey(course);
            if (excludedCourseKeys.contains(courseKey)) {
                continue;
            }

            if (requiredNames.contains(normalizedName)) {
                requiredCourses.putIfAbsent(
                        courseKey,
                        new CategoryCourseDto(course.courseCode(), course.courseName(), course.credit())
                );
                continue;
            }

            if (optionalNames.contains(normalizedName)) {
                optionalCandidates.add(course);
            }
        }

        optionalCandidates.sort(
                Comparator.comparing((CompletedCourseUploadRowDto course) -> toDecimal(course.credit())).reversed()
                        .thenComparing(course -> defaultString(course.courseCode()))
                        .thenComparing(course -> defaultString(course.courseName()))
        );

        Map<String, CategoryCourseDto> selectedOptionalCourses = new LinkedHashMap<>();
        for (CompletedCourseUploadRowDto course : optionalCandidates) {
            if (rule.optionalCourseLimit() > 0 && selectedOptionalCourses.size() >= rule.optionalCourseLimit()) {
                break;
            }
            String courseKey = uniqueCourseKey(course);
            selectedOptionalCourses.putIfAbsent(
                    courseKey,
                    new CategoryCourseDto(course.courseCode(), course.courseName(), course.credit())
            );
        }

        List<CategoryCourseDto> matched = new ArrayList<>(requiredCourses.values());
        matched.addAll(selectedOptionalCourses.values());
        return List.copyOf(matched);
    }

    private String uniqueCourseKey(CompletedCourseUploadRowDto course) {
        if (course.courseCode() != null && !course.courseCode().isBlank()) {
            return "CODE:" + course.courseCode().trim();
        }
        return "NAME:" + normalizeCourseName(course.courseName());
    }

    private String uniqueCourseKey(String courseCode, String courseName) {
        if (courseCode != null && !courseCode.isBlank()) {
            return "CODE:" + courseCode.trim();
        }
        return "NAME:" + normalizeCourseName(courseName);
    }

    private Set<String> normalizeMajorFoundationRuleNames(Set<String> courseNames) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String courseName : courseNames) {
            String base = normalizeCourseName(courseName);
            if (base.isBlank()) {
                continue;
            }
            normalized.add(base);
            if ("선형대수".equals(courseName)) {
                normalized.add(normalizeCourseName("선형대수및프로그래밍"));
            } else if ("선형대수및프로그래밍".equals(courseName)) {
                normalized.add(normalizeCourseName("선형대수"));
            } else if ("확률및통계".equals(courseName)) {
                normalized.add(normalizeCourseName("확률통계및프로그래밍"));
            } else if ("확률통계및프로그래밍".equals(courseName)) {
                normalized.add(normalizeCourseName("확률및통계"));
            }
        }
        return normalized;
    }

    private String calculateMajorGradePoint(List<CompletedCourseUploadRowDto> courses) {
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalGradePoints = BigDecimal.ZERO;

        for (CompletedCourseUploadRowDto course : courses) {
            if (!isMajorCategory(course.category()) || !isCountableForGpa(course)) {
                continue;
            }

            BigDecimal credit = toDecimal(course.credit());
            BigDecimal gradePoint = toDecimal(course.gradePoint());
            totalCredits = totalCredits.add(credit);
            totalGradePoints = totalGradePoints.add(credit.multiply(gradePoint));
        }

        if (totalCredits.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }

        return totalGradePoints.divide(totalCredits, 2, RoundingMode.HALF_UP).toPlainString();
    }

    private String calculateAverageGradePoint(List<CompletedCourseUploadRowDto> courses) {
        return transcriptSummaryCalculator.summarize(courses).averageGradePoint();
    }

    private String calculateLiberalGradePoint(List<CompletedCourseUploadRowDto> courses) {
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalGradePoints = BigDecimal.ZERO;

        for (CompletedCourseUploadRowDto course : courses) {
            if (!isLiberalCategory(course.category()) || !isCountableForGpa(course)) {
                continue;
            }

            BigDecimal credit = toDecimal(course.credit());
            BigDecimal gradePoint = toDecimal(course.gradePoint());
            totalCredits = totalCredits.add(credit);
            totalGradePoints = totalGradePoints.add(credit.multiply(gradePoint));
        }

        if (totalCredits.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }

        return totalGradePoints.divide(totalCredits, 2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean isMajorCategory(String category) {
        return hasCategory(category, "전필", "전선", "전기", "전공기초");
    }

    private boolean isLiberalCategory(String category) {
        if (category == null) {
            return false;
        }
        return LIBERAL_GPA_CATEGORIES.contains(category.trim());
    }

    private boolean isDoubleMajorCategory(String category) {
        return hasCategorySet(category, DOUBLE_MAJOR_REQUIRED_CATEGORIES)
                || hasCategorySet(category, DOUBLE_MAJOR_ELECTIVE_CATEGORIES);
    }

    private boolean isDoubleMajorStudent(Student student) {
        if (student.getMajorType() == com.example.congraduation.domain.MajorType.DOUBLE
                || student.getMajorType() == com.example.congraduation.domain.MajorType.DOUBLE_MAJOR) {
            return true;
        }

        return student.getMajorTracks().stream()
                .anyMatch(track -> track.getTrackType() == com.example.congraduation.domain.MajorType.DOUBLE
                        || track.getTrackType() == com.example.congraduation.domain.MajorType.DOUBLE_MAJOR);
    }

    private boolean isDoubleMajorGraduationRequirementSatisfied(
            DoubleMajorGraduationRequirementProgressDto graduationRequirement
    ) {
        return graduationRequirement.satisfied()
                || "NOT_REQUIRED".equalsIgnoreCase(graduationRequirement.status())
                || "MANUAL_CHECK_REQUIRED".equalsIgnoreCase(graduationRequirement.status());
    }

    private String resolveDoubleMajorTrackStatus(
            boolean trackSatisfied,
            DoubleMajorGraduationRequirementProgressDto graduationRequirement
    ) {
        if (trackSatisfied) {
            return "COMPLETED";
        }
        if ("MANUAL_CHECK_REQUIRED".equalsIgnoreCase(graduationRequirement.status())) {
            return "MANUAL_CHECK_REQUIRED";
        }
        return "IN_PROGRESS";
    }

    private List<StudentMajorTrack> resolveAdditionalTracks(Student student) {
        if (!student.getMajorTracks().isEmpty()) {
            return student.getMajorTracks();
        }

        if (!isDoubleMajorStudent(student)) {
            return List.of();
        }

        if (student.getSecondaryMajor() == null || student.getSecondaryMajor().isBlank()) {
            return List.of();
        }

        return List.of(StudentMajorTrack.create(
                com.example.congraduation.domain.MajorType.DOUBLE_MAJOR,
                normalizeMajor(student.getSecondaryMajor()),
                null,
                false
        ));
    }

    private boolean isDoubleMajorType(com.example.congraduation.domain.MajorType trackType) {
        return trackType == com.example.congraduation.domain.MajorType.DOUBLE
                || trackType == com.example.congraduation.domain.MajorType.DOUBLE_MAJOR;
    }

    private boolean isCountableForGpa(CompletedCourseUploadRowDto course) {
        String evaluationMethod = course.evaluationMethod();
        if (evaluationMethod != null && !evaluationMethod.isBlank()) {
            return "GRADE".equalsIgnoreCase(evaluationMethod.trim());
        }

        // Backward compatibility for rows uploaded before evaluationMethod was persisted.
        return toDecimal(course.gradePoint()).compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isPassedForCompletion(CompletedCourseUploadRowDto course) {
        String grade = normalizeGrade(course.grade());
        if ("F".equals(grade) || "FA".equals(grade) || "NP".equals(grade)) {
            return false;
        }

        String evaluationMethod = course.evaluationMethod();
        if (evaluationMethod != null
                && "P/NP".equalsIgnoreCase(evaluationMethod.trim())
                && (grade == null || grade.isBlank())) {
            return false;
        }

        return true;
    }

    private boolean hasCategory(String category, String... candidates) {
        if (category == null) {
            return false;
        }

        return Arrays.stream(candidates)
                .anyMatch(candidate -> candidate.equals(category.trim()));
    }

    private boolean hasCategorySet(String category, Set<String> candidates) {
        if (category == null) {
            return false;
        }
        return candidates.contains(category.trim());
    }

    private boolean containsGraduationWorkKeyword(String courseName) {
        return GRADUATION_WORK_KEYWORDS.stream()
                .anyMatch(courseName::contains);
    }

    private String normalizeMajor(String major) {
        if (major == null) {
            return "";
        }

        String normalized = major.trim();
        if ("건축학전공".equals(normalized)) {
            return "건축학과";
        }
        if ("법학과".equals(normalized) || "법학".equals(normalized)) {
            return "법학전공";
        }
        return normalized;
    }

    private String resolveCurriculumDepartmentCode(String major) {
        return switch (normalizeMajor(major)) {
            case "컴퓨터공학과" -> "CSE";
            case "소프트웨어학과" -> "SW";
            case "건설환경공학과" -> "CIVIL";
            case "데이터사이언스학과", "인공지능데이터사이언스학과" -> "DS";
            case "전자정보통신공학과" -> "EICE";
            case "정보보호학과" -> "SEC";
            case "양자원자력공학과" -> "NUCLEAR";
            case "나노신소재공학과" -> "NANO";
            case "기계공학과" -> "MECH";
            case "건축공학과" -> "ARCH";
            case "우주항공공학전공" -> "AERO";
            case "환경에너지공간융합학과" -> "ENV";
            case "AI로봇학과" -> "AIROBOT";
            case "인공지능학과" -> "AI";
            case "지구자원시스템공학과" -> "ENERGY";
            default -> null;
        };
    }

    private String normalizeCourseName(String courseName) {
        return courseName == null ? "" : courseName.replaceAll("\\s+", "").trim().toLowerCase();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String normalizeGrade(String grade) {
        return grade == null ? null : grade.trim().toUpperCase();
    }

    private boolean isSatisfied(BigDecimal earned, Integer required) {
        if (required == null || required <= 0) {
            return true;
        }
        return earned.compareTo(BigDecimal.valueOf(required)) >= 0;
    }

    private String toPercentString(BigDecimal earned, Integer required) {
        if (required == null || required <= 0) {
            return null;
        }
        return earned.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(required), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static class AreaAccumulator {
        private BigDecimal earnedCredits = BigDecimal.ZERO;
        private final List<CategoryCourseDto> courses = new ArrayList<>();
    }

    private record BalancedLiberalEvaluation(
            CategoryProgressDto progress,
            int requiredAreaCount,
            int completedAreaCount,
            List<BalancedLiberalAreaProgressDto> areaProgresses
    ) {
    }

    private record MajorTrackCreditPolicy(
            int requiredCredits,
            int electiveCredits
    ) {
        private int totalCredits() {
            return requiredCredits + electiveCredits;
        }
    }
}
