package com.example.congraduation.service.plan;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.dto.plan.PlannableCourseCatalogResponseDto;
import com.example.congraduation.dto.plan.PlannableCourseDto;
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

    public PlannableCourseCatalogService(TimetableCatalog timetableCatalog) {
        this.timetableCatalog = timetableCatalog;
    }

    public PlannableCourseCatalogResponseDto getCatalog() {
        return getCatalog(null, null, null, null, null);
    }

    public PlannableCourseCatalogResponseDto getCatalog(
            String keyword,
            String targetGrade,
            String offeredTerm,
            String departmentName,
            String category
    ) {
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
                .filter(accumulator -> matchesDepartment(accumulator, departmentName))
                .filter(accumulator -> matchesCategory(accumulator, category))
                .sorted(Comparator
                        .comparing(CourseAccumulator::courseName)
                        .thenComparing(CourseAccumulator::department)
                        .thenComparing(CourseAccumulator::category))
                .map(accumulator -> new PlannableCourseDto(
                        new ArrayList<>(accumulator.courseCodes()),
                        accumulator.courseName(),
                        accumulator.category(),
                        accumulator.department(),
                        new ArrayList<>(accumulator.targetGrades()),
                        new ArrayList<>(accumulator.credits()),
                        new ArrayList<>(accumulator.offeredTerms())
                ))
                .toList();

        return new PlannableCourseCatalogResponseDto(courses.size(), courses);
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
                    toCourseKey(courseName, resolvedDepartment, resolvedCategory),
                    ignored -> new CourseAccumulator(courseName, resolvedCategory, resolvedDepartment)
            );
            addIfPresent(accumulator.courseCodes(), offering.courseCode());
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

    private boolean matchesDepartment(CourseAccumulator accumulator, String departmentName) {
        if (departmentName == null || departmentName.isBlank()) {
            return true;
        }
        String normalizedDepartment = normalize(departmentName);
        return normalize(accumulator.department()).contains(normalizedDepartment);
    }

    private boolean matchesCategory(CourseAccumulator accumulator, String category) {
        if (category == null || category.isBlank()) {
            return true;
        }
        String normalizedCategory = normalize(category);
        return normalize(accumulator.category()).contains(normalizedCategory);
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

    private String toCourseKey(String courseName, String department, String category) {
        return normalize(courseName) + "|" + normalize(department) + "|" + normalize(category);
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

    private record CourseAccumulator(
            Set<String> courseCodes,
            String courseName,
            String category,
            String department,
            Set<String> targetGrades,
            Set<String> credits,
            Set<String> offeredTerms
    ) {
        private CourseAccumulator(String courseName, String category, String department) {
            this(
                    new LinkedHashSet<>(),
                    courseName,
                    category,
                    department,
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>()
            );
        }
    }
}
