package com.example.congraduation.service.plan;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.plan.PlannableCourseCatalogResponseDto;
import com.example.congraduation.dto.plan.PlannableCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PlannableCourseCatalogService {

    private final TimetableCatalog timetableCatalog;
    private final StudentRepository studentRepository;
    private final TranscriptStorageService transcriptStorageService;

    public PlannableCourseCatalogService(
            TimetableCatalog timetableCatalog,
            StudentRepository studentRepository,
            TranscriptStorageService transcriptStorageService
    ) {
        this.timetableCatalog = timetableCatalog;
        this.studentRepository = studentRepository;
        this.transcriptStorageService = transcriptStorageService;
    }

    public PlannableCourseCatalogResponseDto getCatalog() {
        return getCatalog(null, null, null, null, null, null);
    }

    public PlannableCourseCatalogResponseDto getCatalog(
            Long studentId,
            String keyword,
            String targetGrade,
            String offeredTerm,
            String departmentName,
            String category
    ) {
        Student student = resolveStudent(studentId);
        String referenceDepartmentName = resolveReferenceDepartmentName(student, departmentName);
        Set<String> blockedRetakeCourseCodes = resolveBlockedRetakeCourseCodes(student);

        Map<String, CourseAccumulator> deduplicated = new LinkedHashMap<>();
        List<TimetableTermData> terms = resolveTerms(offeredTerm);
        terms.forEach(term -> parseTerm(term, deduplicated));

        if (deduplicated.isEmpty()) {
            throw new IllegalStateException("적재된 시간표 데이터가 없습니다.");
        }

        List<PlannableCourseDto> courses = deduplicated.values().stream()
                .filter(accumulator -> matchesKeyword(accumulator, keyword))
                .filter(accumulator -> matchesTargetGrade(accumulator, targetGrade))
                .filter(accumulator -> matchesOfferedTerm(accumulator, offeredTerm))
                .filter(accumulator -> !isBlockedRetakeCourse(accumulator, blockedRetakeCourseCodes))
                .filter(accumulator -> matchesDepartment(accumulator, referenceDepartmentName, student != null))
                .filter(accumulator -> matchesCategory(accumulator, category, referenceDepartmentName))
                .sorted(Comparator
                        .comparing(CourseAccumulator::courseName)
                        .thenComparing(accumulator -> resolveCategory(accumulator, referenceDepartmentName)))
                .map(accumulator -> new PlannableCourseDto(
                        new ArrayList<>(accumulator.courseCodes()),
                        accumulator.courseName(),
                        resolveCategory(accumulator, referenceDepartmentName),
                        new ArrayList<>(accumulator.departments()),
                        new ArrayList<>(accumulator.targetGrades()),
                        new ArrayList<>(accumulator.credits()),
                        new ArrayList<>(accumulator.offeredTerms())
                ))
                .toList();

        return new PlannableCourseCatalogResponseDto(courses.size(), courses);
    }

    private Student resolveStudent(Long studentId) {
        if (studentId == null) {
            return null;
        }
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
    }

    private String resolveReferenceDepartmentName(Student student, String departmentName) {
        if (student != null && !isBlank(student.getMajor())) {
            return student.getMajor();
        }
        return departmentName;
    }

    private List<TimetableTermData> resolveTerms(String offeredTerm) {
        if (offeredTerm == null || offeredTerm.isBlank()) {
            return timetableCatalog.availableTerms();
        }
        String[] parts = offeredTerm.trim().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("offeredTerm 형식은 YYYY-S 여야 합니다. 예: 2025-2");
        }

        int termYear = Integer.parseInt(parts[0]);
        int semester = Integer.parseInt(parts[1]);
        return List.of(
                timetableCatalog.findTerm(termYear, semester)
                        .orElseThrow(() -> new IllegalArgumentException("시간표 데이터 없음: " + offeredTerm))
        );
    }

    private void parseTerm(TimetableTermData term, Map<String, CourseAccumulator> deduplicated) {
        String termCode = term.termYear() + "-" + term.semester();
        for (TimetableOffering offering : term.offerings()) {
            if (offering == null || isBlank(offering.courseName())) {
                continue;
            }

            String courseName = offering.courseName().trim();
            String resolvedDepartment = normalizeDisplayText(firstNonBlank(offering.hostDepartment(), offering.openingDepartment()));
            String resolvedCategory = normalizeDisplayText(offering.category());

            CourseAccumulator accumulator = deduplicated.computeIfAbsent(
                    toCourseKey(courseName, resolvedCategory),
                    ignored -> new CourseAccumulator(courseName, resolvedCategory)
            );
            addIfPresent(accumulator.courseCodes(), offering.courseCode());
            accumulator.departments().add(resolvedDepartment);
            addIfPresent(accumulator.targetGrades(), offering.gradeYear());
            if (offering.credits() != null) {
                accumulator.credits().add(formatCredit(offering.credits()));
            }
            accumulator.offeredTerms().add(termCode);
        }
    }

    private boolean matchesKeyword(CourseAccumulator accumulator, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = normalize(keyword);
        if (normalize(accumulator.courseName()).contains(normalizedKeyword)) {
            return true;
        }
        return accumulator.courseCodes().stream()
                .map(this::normalize)
                .anyMatch(code -> code.contains(normalizedKeyword));
    }

    private boolean matchesTargetGrade(CourseAccumulator accumulator, String targetGrade) {
        if (targetGrade == null || targetGrade.isBlank()) {
            return true;
        }
        String normalizedGrade = normalize(targetGrade);
        return accumulator.targetGrades().stream()
                .map(this::normalize)
                .anyMatch(grade -> grade.contains(normalizedGrade));
    }

    private boolean matchesOfferedTerm(CourseAccumulator accumulator, String offeredTerm) {
        if (offeredTerm == null || offeredTerm.isBlank()) {
            return true;
        }
        String normalizedTerm = normalize(offeredTerm);
        return accumulator.offeredTerms().stream()
                .map(this::normalize)
                .anyMatch(term -> term.equals(normalizedTerm));
    }

    private boolean matchesDepartment(CourseAccumulator accumulator, String departmentName, boolean personalizedSearch) {
        if (departmentName == null || departmentName.isBlank()) {
            return true;
        }
        if (personalizedSearch) {
            return true;
        }
        if (isGloballyPlannableCourse(accumulator.courseName())) {
            return true;
        }
        if (!isDepartmentScopedCategory(accumulator.category())) {
            return true;
        }
        String normalizedDepartment = normalize(departmentName);
        return accumulator.departments().stream()
                .map(this::normalize)
                .anyMatch(department -> department.contains(normalizedDepartment));
    }

    private boolean matchesCategory(CourseAccumulator accumulator, String category, String departmentName) {
        if (category == null || category.isBlank()) {
            return true;
        }
        String normalizedCategory = normalize(category);
        return normalize(resolveCategory(accumulator, departmentName)).contains(normalizedCategory);
    }

    private String resolveCategory(CourseAccumulator accumulator, String departmentName) {
        String rawCategory = accumulator.category();
        if (!isMajorCategory(rawCategory)) {
            return rawCategory;
        }
        if (isGloballyPlannableCourse(accumulator.courseName())) {
            return rawCategory;
        }
        if (departmentName == null || departmentName.isBlank()) {
            return rawCategory;
        }
        if (isStudentMajorDepartment(accumulator, departmentName)) {
            return rawCategory;
        }
        return "전공선택";
    }

    private boolean isStudentMajorDepartment(CourseAccumulator accumulator, String departmentName) {
        String normalizedDepartment = normalize(departmentName);
        return accumulator.departments().stream()
                .map(this::normalize)
                .anyMatch(department -> department.contains(normalizedDepartment));
    }

    private Set<String> resolveBlockedRetakeCourseCodes(Student student) {
        if (student == null || !transcriptStorageService.hasTranscript(student.getId())) {
            return Set.of();
        }
        Set<String> blockedCourseCodes = new LinkedHashSet<>();
        for (CompletedCourseUploadRowDto course : transcriptStorageService.getLatestTranscriptRows(student.getId())) {
            String courseCode = normalizeCourseCode(course.courseCode());
            if (courseCode.isBlank()) {
                continue;
            }
            if (isRetakeBlocked(course)) {
                blockedCourseCodes.add(courseCode);
            }
        }
        return blockedCourseCodes;
    }

    private boolean isBlockedRetakeCourse(CourseAccumulator accumulator, Set<String> blockedRetakeCourseCodes) {
        if (blockedRetakeCourseCodes.isEmpty()) {
            return false;
        }
        return accumulator.courseCodes().stream()
                .map(this::normalizeCourseCode)
                .anyMatch(blockedRetakeCourseCodes::contains);
    }

    private boolean isRetakeBlocked(CompletedCourseUploadRowDto course) {
        String normalizedGrade = normalizeGrade(course.grade());
        if (Set.of("A+", "A0", "B+", "B0").contains(normalizedGrade)) {
            return true;
        }
        if (course.evaluationMethod() != null && "GRADE".equalsIgnoreCase(course.evaluationMethod().trim())) {
            return parseGradePoint(course.gradePoint()).compareTo(BigDecimal.valueOf(3.0)) >= 0;
        }
        return false;
    }

    private void addIfPresent(Set<String> target, String value) {
        if (!isBlank(value)) {
            target.add(value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String primary, String fallback) {
        return isBlank(primary) ? fallback : primary;
    }

    private String normalizeDisplayText(String value) {
        return isBlank(value) ? "미지정" : value.trim();
    }

    private String toCourseKey(String courseName, String category) {
        return normalize(courseName) + "|" + normalize(category);
    }

    private String formatCredit(Double credits) {
        if (credits == null) {
            return "";
        }
        if (credits == Math.rint(credits)) {
            return Integer.toString(credits.intValue());
        }
        return credits.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeCourseCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim();
    }

    private String normalizeGrade(String grade) {
        return grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal parseGradePoint(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private boolean isDepartmentScopedCategory(String category) {
        return isMajorCategory(category);
    }

    private boolean isMajorCategory(String category) {
        String normalizedCategory = normalize(category);
        return normalizedCategory.contains("전공");
    }

    private boolean isGloballyPlannableCourse(String courseName) {
        String normalizedCourseName = normalize(courseName);
        return normalizedCourseName.contains("자기주도창의전공");
    }

    private record CourseAccumulator(
            Set<String> courseCodes,
            String courseName,
            String category,
            Set<String> departments,
            Set<String> targetGrades,
            Set<String> credits,
            Set<String> offeredTerms
    ) {
        private CourseAccumulator(String courseName, String category) {
            this(
                    new LinkedHashSet<>(),
                    courseName,
                    category,
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>()
            );
        }
    }
}
