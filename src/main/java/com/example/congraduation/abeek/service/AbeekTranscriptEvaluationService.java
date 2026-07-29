package com.example.congraduation.abeek.service;

import com.example.congraduation.abeek.domain.AbeekStudent;
import com.example.congraduation.abeek.domain.CourseMaster;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.StudentEnrollment;
import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse;
import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse.MatchedCourseDto;
import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse.UnmatchedCourseDto;
import com.example.congraduation.abeek.repository.AbeekStudentRepository;
import com.example.congraduation.abeek.repository.CourseMasterRepository;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.service.transcript.TranscriptExcelParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbeekTranscriptEvaluationService {

    private static final Pattern SEMESTER_PATTERN = Pattern.compile("(\\d+)");

    private final TranscriptExcelParser transcriptExcelParser;
    private final AbeekDepartmentCatalog departmentCatalog;
    private final CourseMasterRepository courseMasterRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AbeekStudentRepository abeekStudentRepository;
    private final AbeekEvaluationService abeekEvaluationService;

    @Transactional
    public AbeekTranscriptEvaluationResponse evaluateFromTranscript(
            MultipartFile file,
            String studentId,
            String studentName,
            Integer entranceYearOverride,
            Integer graduationAbeekYearOverride,
            String departmentCodeOverride
    ) {
        List<CompletedCourseUploadRowDto> rows = transcriptExcelParser.parse(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("성적표에서 이수 과목을 찾지 못했습니다.");
        }

        AbeekDepartmentCatalog.DepartmentInfo department = resolveDepartment(rows, departmentCodeOverride);
        int entranceYear = entranceYearOverride != null ? entranceYearOverride : inferEntranceYear(rows, studentId);
        int graduationAbeekYear = graduationAbeekYearOverride != null
                ? graduationAbeekYearOverride
                : inferGraduationAbeekYear(rows);

        Map<String, CourseMaster> mastersByNormalizedName = buildMasterIndex();
        List<MatchedCourseDto> matches = new ArrayList<>();
        List<UnmatchedCourseDto> unmatched = new ArrayList<>();
        List<StudentEnrollment> enrollments = new ArrayList<>();

        for (CompletedCourseUploadRowDto row : rows) {
            Optional<CourseMaster> matched = matchCourse(row.courseName(), mastersByNormalizedName);
            if (matched.isEmpty()) {
                unmatched.add(UnmatchedCourseDto.builder()
                        .transcriptCourseCode(row.courseCode())
                        .transcriptCourseName(row.courseName())
                        .category(row.category())
                        .reason("ABEEK 과목 마스터에서 동일 과목명을 찾지 못함")
                        .build());
                continue;
            }

            CourseMaster master = matched.get();
            int takenYear = parseInt(row.year(), entranceYear);
            int takenSemester = parseSemester(row.semester());
            int credits = parseCredits(row.credit());
            boolean passed = isPassed(row.grade());
            double designCredits = resolveDesignCredits(
                    department.abeekCode(), master.getCourseCode(), takenYear, entranceYear);

            enrollments.add(StudentEnrollment.builder()
                    .courseMaster(master)
                    .credits(credits)
                    .designCredits(designCredits)
                    .takenYear(takenYear)
                    .takenSemester(takenSemester)
                    .passed(passed)
                    .build());

            matches.add(MatchedCourseDto.builder()
                    .transcriptCourseCode(row.courseCode())
                    .transcriptCourseName(row.courseName())
                    .abeekCourseCode(master.getCourseCode())
                    .abeekCourseName(master.getName())
                    .credits(credits)
                    .designCredits(designCredits)
                    .takenYear(takenYear)
                    .takenSemester(takenSemester)
                    .passed(passed)
                    .build());
        }

        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("ABEEK 커리큘럼과 매칭된 과목이 없습니다. 학과/데이터 시드를 확인하세요.");
        }

        String resolvedStudentId = (studentId == null || studentId.isBlank())
                ? "transcript-" + System.currentTimeMillis()
                : studentId.trim();
        String resolvedName = (studentName == null || studentName.isBlank()) ? resolvedStudentId : studentName.trim();

        AbeekStudent student = upsertStudent(
                resolvedStudentId,
                resolvedName,
                entranceYear,
                graduationAbeekYear,
                department.name(),
                department.abeekCode(),
                enrollments
        );

        return AbeekTranscriptEvaluationResponse.builder()
                .studentId(student.getStudentId())
                .studentName(student.getName())
                .inferredSejongDepartmentCode(department.sejongCode())
                .departmentCode(department.abeekCode())
                .departmentName(department.name())
                .entranceYear(entranceYear)
                .graduationAbeekYear(graduationAbeekYear)
                .totalCourses(rows.size())
                .matchedCourses(matches.size())
                .unmatchedCourses(unmatched.size())
                .matches(matches)
                .unmatched(unmatched)
                .evaluation(abeekEvaluationService.evaluate(student.getStudentId()))
                .build();
    }

    private AbeekStudent upsertStudent(
            String studentId,
            String name,
            int entranceYear,
            int graduationAbeekYear,
            String departmentName,
            String departmentCode,
            List<StudentEnrollment> enrollments
    ) {
        AbeekStudent student = abeekStudentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseGet(() -> AbeekStudent.builder().studentId(studentId).build());

        student.setStudentId(studentId);
        student.setName(name);
        student.setEntranceYear(entranceYear);
        student.setGraduationAbeekYear(graduationAbeekYear);
        student.setDepartment(departmentName);
        student.setDepartmentCode(departmentCode);
        if (student.getEnrollments() == null) {
            student.setEnrollments(new ArrayList<>());
        }
        student.getEnrollments().clear();
        for (StudentEnrollment enrollment : enrollments) {
            student.addEnrollment(enrollment);
        }
        return abeekStudentRepository.save(student);
    }

    private AbeekDepartmentCatalog.DepartmentInfo resolveDepartment(
            List<CompletedCourseUploadRowDto> rows,
            String departmentCodeOverride
    ) {
        if (departmentCodeOverride != null && !departmentCodeOverride.isBlank()) {
            return departmentCatalog.findByAbeekCode(departmentCodeOverride)
                    .orElseGet(() -> new AbeekDepartmentCatalog.DepartmentInfo(
                            null,
                            departmentCodeOverride.trim().toUpperCase(Locale.ROOT),
                            departmentCodeOverride.trim().toUpperCase(Locale.ROOT)
                    ));
        }

        Map<String, Long> counts = rows.stream()
                .filter(row -> row.openingDepartmentCode() != null)
                .filter(row -> !departmentCatalog.isSharedOpeningCode(row.openingDepartmentCode()))
                .filter(row -> isMajorLike(row.category()))
                .collect(Collectors.groupingBy(
                        CompletedCourseUploadRowDto::openingDepartmentCode,
                        Collectors.counting()
                ));

        if (counts.isEmpty()) {
            counts = rows.stream()
                    .filter(row -> row.openingDepartmentCode() != null)
                    .filter(row -> !departmentCatalog.isSharedOpeningCode(row.openingDepartmentCode()))
                    .collect(Collectors.groupingBy(
                            CompletedCourseUploadRowDto::openingDepartmentCode,
                            Collectors.counting()
                    ));
        }

        String topSejongCode = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("3210");

        return departmentCatalog.findBySejongCode(topSejongCode)
                .orElseGet(() -> new AbeekDepartmentCatalog.DepartmentInfo(topSejongCode, "CSE", "컴퓨터공학과"));
    }

    private boolean isMajorLike(String category) {
        if (category == null) {
            return false;
        }
        String value = category.replace(" ", "");
        return value.contains("전필") || value.contains("전선") || value.contains("전공");
    }

    private int inferEntranceYear(List<CompletedCourseUploadRowDto> rows, String studentId) {
        if (studentId != null && studentId.length() >= 2 && Character.isDigit(studentId.charAt(0))) {
            try {
                int yy = Integer.parseInt(studentId.substring(0, 2));
                return 2000 + yy;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return rows.stream()
                .map(row -> parseInt(row.year(), Integer.MAX_VALUE))
                .filter(year -> year != Integer.MAX_VALUE)
                .min(Integer::compareTo)
                .orElse(2021);
    }

    private int inferGraduationAbeekYear(List<CompletedCourseUploadRowDto> rows) {
        return rows.stream()
                .map(row -> parseInt(row.year(), Integer.MIN_VALUE))
                .filter(year -> year != Integer.MIN_VALUE)
                .max(Integer::compareTo)
                .orElse(2026);
    }

    private Map<String, CourseMaster> buildMasterIndex() {
        Map<String, CourseMaster> index = new HashMap<>();
        for (CourseMaster master : courseMasterRepository.findAll()) {
            index.putIfAbsent(normalizeCourseName(master.getName()), master);
        }
        return index;
    }

    private Optional<CourseMaster> matchCourse(String transcriptName, Map<String, CourseMaster> index) {
        if (transcriptName == null || transcriptName.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeCourseName(transcriptName);
        CourseMaster exact = index.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }

        String withoutParen = normalizeCourseName(transcriptName.replaceAll("\\([^)]*\\)", ""));
        CourseMaster byParen = index.get(withoutParen);
        if (byParen != null) {
            return Optional.of(byParen);
        }

        return index.entrySet().stream()
                .filter(entry -> entry.getKey().contains(normalized) || normalized.contains(entry.getKey()))
                .min(Comparator.comparingInt(entry -> Math.abs(entry.getKey().length() - normalized.length())))
                .map(Map.Entry::getValue);
    }

    private String normalizeCourseName(String name) {
        return name.replaceAll("\\s+", "")
                .replace('：', ':')
                .replace('（', '(')
                .replace('）', ')')
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private double resolveDesignCredits(
            String departmentCode,
            String courseCode,
            int takenYear,
            int entranceYear
    ) {
        Optional<CurriculumCourse> takenYearCourse = curriculumCourseRepository
                .findByCurriculumYearAndDepartmentCodeAndCourseMaster_CourseCode(takenYear, departmentCode, courseCode);
        if (takenYearCourse.isPresent()) {
            return takenYearCourse.get().getDesignCredits();
        }
        return curriculumCourseRepository
                .findByCurriculumYearAndDepartmentCodeAndCourseMaster_CourseCode(entranceYear, departmentCode, courseCode)
                .map(CurriculumCourse::getDesignCredits)
                .orElse(0.0);
    }

    private int parseSemester(String semester) {
        if (semester == null || semester.isBlank()) {
            return 1;
        }
        Matcher matcher = SEMESTER_PATTERN.matcher(semester);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }

    private int parseCredits(String credit) {
        if (credit == null || credit.isBlank()) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(credit.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return (int) Math.round(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean isPassed(String grade) {
        if (grade == null || grade.isBlank()) {
            return true;
        }
        String normalized = grade.trim().toUpperCase(Locale.ROOT);
        return !(normalized.equals("F") || normalized.equals("NP") || normalized.equals("N") || normalized.equals("U"));
    }
}
