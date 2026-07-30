package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.StudentMajorTrack;
import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.graduation.BalancedLiberalAreaProgressDto;
import com.example.congraduation.dto.graduation.CreditProgressDto;
import com.example.congraduation.dto.graduation.CategoryProgressDto;
import com.example.congraduation.dto.graduation.GraduationWorkProgressDto;
import com.example.congraduation.dto.graduation.GraduationProgressResponseDto;
import com.example.congraduation.dto.graduation.MajorTrackProgressDto;
import com.example.congraduation.dto.graduation.MajorCreditSummaryDto;
import com.example.congraduation.dto.graduation.MajorTrackRequiredCourseProgressDto;
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

    private final StudentRepository studentRepository;
    private final TranscriptStorageService transcriptStorageService;
    private final TranscriptSummaryCalculator transcriptSummaryCalculator;
    private final PlannedCourseService plannedCourseService;
    private final DepartmentCurriculumPolicyService policyService;
    private final BalancedLiberalCoursePolicyService balancedLiberalCoursePolicyService;
    private final DoubleMajorRequiredCoursePolicyService doubleMajorRequiredCoursePolicyService;

    public GraduationProgressService(
            StudentRepository studentRepository,
            TranscriptStorageService transcriptStorageService,
            TranscriptSummaryCalculator transcriptSummaryCalculator,
            PlannedCourseService plannedCourseService,
            DepartmentCurriculumPolicyService policyService,
            BalancedLiberalCoursePolicyService balancedLiberalCoursePolicyService,
            DoubleMajorRequiredCoursePolicyService doubleMajorRequiredCoursePolicyService
    ) {
        this.studentRepository = studentRepository;
        this.transcriptStorageService = transcriptStorageService;
        this.transcriptSummaryCalculator = transcriptSummaryCalculator;
        this.plannedCourseService = plannedCourseService;
        this.policyService = policyService;
        this.balancedLiberalCoursePolicyService = balancedLiberalCoursePolicyService;
        this.doubleMajorRequiredCoursePolicyService = doubleMajorRequiredCoursePolicyService;
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
        List<CompletedCourseUploadRowDto> evaluationCourses = new ArrayList<>(normalizedCourses);
        evaluationCourses.addAll(normalizedProjectedCourses);
        TranscriptSummaryDto transcriptSummary = transcriptSummaryCalculator.summarize(evaluationCourses);
        BalancedLiberalEvaluation balancedLiberalEvaluation = evaluateBalancedLiberal(student, evaluationCourses);
        MajorTrackCreditPolicy primaryMajorPolicy = resolvePrimaryMajorPolicy(student);
        Map<String, Integer> categoryRequirements = resolveCategoryRequirements(policy, primaryMajorPolicy);
        List<MajorTrackProgressDto> majorTracks =
                buildMajorTrackProgresses(student, transcriptSummary.categorySummaries(), evaluationCourses);
        GraduationWorkProgressDto graduationWork = buildGraduationWorkProgress(student, evaluationCourses);
        CreditProgressDto totalCreditsProgress = buildCreditProgress(transcriptSummary.totalCredits(), policy.graduationCredits());
        CategoryProgressDto commonLiberalProgress =
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.commonLiberalCredits(), "공필", "교필");
        CategoryProgressDto electiveLiberalProgress =
                buildCategoryProgress(transcriptSummary.categorySummaries(), 0, "교선");
        CategoryProgressDto academicFoundationProgress =
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.academicFoundationCredits(), "기필", "학문기초");
        CategoryProgressDto majorFoundationProgress = buildCategoryProgress(
                transcriptSummary.categorySummaries(),
                isDoubleMajorStudent(student) ? null : policy.majorFoundationCredits(),
                "전기",
                "전공기초"
        );
        MajorCreditSummaryDto majorCreditSummary =
                buildMajorCreditSummary(transcriptSummary, policy, primaryMajorPolicy);
        List<CategorySummaryDto> categorySummaries =
                applyCategoryRequirements(transcriptSummary.categorySummaries(), categoryRequirements);
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
                majorTracks,
                graduationWork,
                totalCreditsProgress,
                commonLiberalProgress,
                electiveLiberalProgress,
                balancedLiberalEvaluation.progress(),
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
                categorySummaries
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
            if (!"COMPLETED".equalsIgnoreCase(majorTrack.status())) {
                blockers.add(majorTrack.department() + " 복수전공 요건을 충족하지 못했습니다.");
            }
        }

        for (CategorySummaryDto categorySummary : categorySummaries) {
            if (categorySummary.requiredCredits() == null || categorySummary.requiredCredits().isBlank()) {
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

            String area = balancedLiberalCoursePolicyService.resolveArea(student.getAdmissionYear(), course);
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
        List<StudentMajorTrack> doubleMajorTracks = resolveDoubleMajorTracks(student);
        return doubleMajorTracks.stream()
                .filter(track -> track.getTrackType() == com.example.congraduation.domain.MajorType.DOUBLE_MAJOR
                        || track.getTrackType() == com.example.congraduation.domain.MajorType.DOUBLE)
                .map(track -> buildDoubleMajorProgress(student, track, categorySummaries, courses))
                .toList();
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
        MajorTrackRequiredCourseProgressDto requiredCourseProgress = new MajorTrackRequiredCourseProgressDto(
                requiredCourseEvaluation.policyApplied(),
                requiredCourseEvaluation.policyApplied() ? requiredCourseEvaluation.requiredCourseCount() : null,
                requiredCourseEvaluation.policyApplied() ? requiredCourseEvaluation.completedCourseCount() : null,
                requiredCourseEvaluation.satisfied(),
                requiredCourseEvaluation.completedCourses(),
                requiredCourseEvaluation.missingCourses()
        );
        boolean creditSatisfied = isSatisfied(required, policy.requiredCredits()) && isSatisfied(elective, policy.electiveCredits());
        boolean trackSatisfied = creditSatisfied && requiredCourseEvaluation.satisfied();

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
                "복필/복선",
                trackSatisfied ? "COMPLETED" : "IN_PROGRESS"
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
        if (policy.majorFoundationCredits() != null) {
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
            MajorTrackCreditPolicy primaryMajorPolicy
    ) {
        BigDecimal majorRequired = creditOf(transcriptSummary.categorySummaries(), "전필");
        BigDecimal majorElective = creditOf(transcriptSummary.categorySummaries(), "전선");
        BigDecimal majorFoundation = creditOf(transcriptSummary.categorySummaries(), "전기", "전공기초");
        BigDecimal majorTotal = primaryMajorPolicy == null
                ? majorRequired.add(majorElective).add(majorFoundation)
                : majorRequired.add(majorElective);
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
                formatDecimal(majorFoundation)
        );
    }

    private List<CategorySummaryDto> applyCategoryRequirements(
            List<CategorySummaryDto> categorySummaries,
            Map<String, Integer> requirements
    ) {
        Map<String, CategorySummaryDto> merged = new LinkedHashMap<>();

        for (CategorySummaryDto summary : categorySummaries) {
            int required = requirements.getOrDefault(summary.category(), 0);
            BigDecimal earned = new BigDecimal(summary.earnedCredits());
            merged.put(summary.category(), new CategorySummaryDto(
                    summary.category(),
                    summary.earnedCredits(),
                    formatRequired(required),
                    isSatisfied(earned, required),
                    toPercentString(earned, required),
                    summary.courses()
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

    private BigDecimal creditOf(List<CategorySummaryDto> summaries, String... categories) {
        return summaries.stream()
                .filter(summary -> Arrays.stream(categories).anyMatch(category -> summary.category().equals(category)))
                .findFirst()
                .map(summary -> new BigDecimal(summary.earnedCredits()))
                .orElse(BigDecimal.ZERO);
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
        return new CategoryProgressDto(
                formatDecimal(earned),
                formatRequired(requiredCredits),
                isSatisfied(earned, requiredCredits),
                toPercentString(earned, requiredCredits)
        );
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

    private List<StudentMajorTrack> resolveDoubleMajorTracks(Student student) {
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

    private boolean isCountableForGpa(CompletedCourseUploadRowDto course) {
        String evaluationMethod = course.evaluationMethod();
        if (evaluationMethod != null && !evaluationMethod.isBlank()) {
            return "GRADE".equalsIgnoreCase(evaluationMethod.trim());
        }

        // Backward compatibility for rows uploaded before evaluationMethod was persisted.
        return toDecimal(course.gradePoint()).compareTo(BigDecimal.ZERO) > 0;
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

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private boolean isSatisfied(BigDecimal earned, Integer required) {
        if (required == null || required <= 0) {
            return false;
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
