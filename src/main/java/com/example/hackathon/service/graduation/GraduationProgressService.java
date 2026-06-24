package com.example.hackathon.service.graduation;

import com.example.hackathon.domain.Student;
import com.example.hackathon.dto.graduation.CreditProgressDto;
import com.example.hackathon.dto.graduation.CategoryProgressDto;
import com.example.hackathon.dto.graduation.GraduationProgressResponseDto;
import com.example.hackathon.dto.graduation.MajorCreditSummaryDto;
import com.example.hackathon.dto.transcript.CategorySummaryDto;
import com.example.hackathon.dto.transcript.CompletedCourseUploadRowDto;
import com.example.hackathon.dto.transcript.TranscriptSummaryDto;
import com.example.hackathon.repository.student.StudentRepository;
import com.example.hackathon.service.transcript.TranscriptStorageService;
import com.example.hackathon.service.transcript.TranscriptSummaryCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;

@Service
public class GraduationProgressService {

    private final StudentRepository studentRepository;
    private final TranscriptStorageService transcriptStorageService;
    private final TranscriptSummaryCalculator transcriptSummaryCalculator;
    private final DepartmentCurriculumPolicyService policyService;

    public GraduationProgressService(
            StudentRepository studentRepository,
            TranscriptStorageService transcriptStorageService,
            TranscriptSummaryCalculator transcriptSummaryCalculator,
            DepartmentCurriculumPolicyService policyService
    ) {
        this.studentRepository = studentRepository;
        this.transcriptStorageService = transcriptStorageService;
        this.transcriptSummaryCalculator = transcriptSummaryCalculator;
        this.policyService = policyService;
    }

    public GraduationProgressResponseDto evaluate(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.getLatestTranscriptRows(studentId);
        DepartmentCurriculumPolicy policy = policyService.resolve(student);
        List<CompletedCourseUploadRowDto> normalizedCourses = normalizeCoursesForPolicy(courses, policy);
        TranscriptSummaryDto transcriptSummary = transcriptSummaryCalculator.summarize(normalizedCourses);

        return new GraduationProgressResponseDto(
                student.getId(),
                student.getAdmissionYear(),
                student.getMajor(),
                student.getMajorType(),
                student.getSecondaryMajor(),
                buildCreditProgress(transcriptSummary.totalCredits(), policy.graduationCredits()),
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.commonLiberalCredits(), "공필", "교필"),
                buildCategoryProgress(transcriptSummary.categorySummaries(), 0, "교선"),
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.balancedLiberalCredits(), "균필"),
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.academicFoundationCredits(), "기필", "학문기초"),
                buildCategoryProgress(transcriptSummary.categorySummaries(), policy.majorFoundationCredits(), "전기", "전공기초"),
                transcriptSummary.averageGradePoint(),
                calculateMajorGradePoint(normalizedCourses),
                buildMajorCreditSummary(transcriptSummary, policy),
                applyCategoryRequirements(transcriptSummary.categorySummaries(), policy.categoryRequiredCredits())
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
            DepartmentCurriculumPolicy policy
    ) {
        BigDecimal majorRequired = creditOf(transcriptSummary.categorySummaries(), "전필");
        BigDecimal majorElective = creditOf(transcriptSummary.categorySummaries(), "전선");
        BigDecimal majorFoundation = creditOf(transcriptSummary.categorySummaries(), "전기", "전공기초");
        BigDecimal majorTotal = majorRequired.add(majorElective).add(majorFoundation);

        return new MajorCreditSummaryDto(
                formatDecimal(majorTotal),
                formatRequired(policy.majorTotalCredits()),
                isSatisfied(majorTotal, policy.majorTotalCredits()),
                toPercentString(majorTotal, policy.majorTotalCredits()),
                formatDecimal(majorRequired),
                formatRequired(policy.majorRequiredCredits()),
                isSatisfied(majorRequired, policy.majorRequiredCredits()),
                toPercentString(majorRequired, policy.majorRequiredCredits()),
                formatDecimal(majorElective),
                formatRequired(policy.majorElectiveCredits()),
                isSatisfied(majorElective, policy.majorElectiveCredits()),
                toPercentString(majorElective, policy.majorElectiveCredits()),
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

    private boolean isMajorCategory(String category) {
        return hasCategory(category, "전필", "전선", "전기", "전공기초");
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
}
