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
import com.example.congraduation.abeek.repository.StudentEnrollmentRepository;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.service.transcript.TranscriptExcelParser;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final TranscriptStorageService transcriptStorageService;
    private final AbeekDepartmentCatalog departmentCatalog;
    private final CourseMasterRepository courseMasterRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AbeekStudentRepository abeekStudentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
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
        List<CompletedCourseUploadRowDto> rows = parseTranscriptFile(file);
        return evaluateFromRows(
                rows,
                studentId,
                studentName,
                entranceYearOverride,
                graduationAbeekYearOverride,
                departmentCodeOverride,
                null
        );
    }

    /**
     * 이미 업로드된 기이수성적으로 ABEEK 판정.
     * studentDbId(DB PK) 또는 studentNo(학번) 중 하나로 학생을 찾는다.
     */
    @Transactional
    public AbeekTranscriptEvaluationResponse evaluateFromStoredTranscript(
            Long studentDbId,
            String studentNo,
            Integer entranceYearOverride,
            Integer graduationAbeekYearOverride,
            String departmentCodeOverride
    ) {
        Student student = resolveAppStudent(studentDbId, studentNo);
        List<CompletedCourseUploadRowDto> rows = transcriptStorageService.getLatestTranscriptRows(student.getId());
        Integer entrance = entranceYearOverride != null ? entranceYearOverride : student.getAdmissionYear();
        return evaluateFromRows(
                rows,
                student.getStudentNo(),
                student.getName(),
                entrance,
                graduationAbeekYearOverride,
                departmentCodeOverride,
                student.getMajor()
        );
    }

    /**
     * 파싱된 성적 행으로 ABEEK 학생/enrollment upsert 후 판정.
     * studentId 인자는 학번(studentNo)이다. DB PK가 아님.
     */
    @Transactional
    public AbeekTranscriptEvaluationResponse evaluateFromRows(
            List<CompletedCourseUploadRowDto> rows,
            String studentId,
            String studentName,
            Integer entranceYearOverride,
            Integer graduationAbeekYearOverride,
            String departmentCodeOverride,
            String preferredDepartmentName
    ) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "성적 데이터가 비어 있습니다. 기이수성적 업로드/파싱 결과를 확인하세요.");
        }

        AbeekDepartmentCatalog.DepartmentInfo department = resolveDepartment(
                rows, departmentCodeOverride, preferredDepartmentName);
        int entranceYear = entranceYearOverride != null
                ? entranceYearOverride
                : inferEntranceYear(rows, studentId);
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
            String sample = unmatched.stream()
                    .limit(5)
                    .map(u -> u.getTranscriptCourseName() == null ? "?" : u.getTranscriptCourseName())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(String.format(
                    "ABEEK 커리큘럼과 매칭된 과목이 없습니다. (전체 %d과목, 미매칭 %d) "
                            + "학과코드=%s, 입학년도=%d, 졸업ABEEK연도=%d. 미매칭 예시: %s",
                    rows.size(),
                    unmatched.size(),
                    department.abeekCode(),
                    entranceYear,
                    graduationAbeekYear,
                    sample.isBlank() ? "(없음)" : sample
            ));
        }

        String resolvedStudentId = (studentId == null || studentId.isBlank())
                ? "transcript-" + System.currentTimeMillis()
                : studentId.trim();
        String resolvedName = (studentName == null || studentName.isBlank()) ? resolvedStudentId : studentName.trim();

        List<StudentEnrollment> uniqueEnrollments = dedupeEnrollments(enrollments);

        AbeekStudent student = upsertStudent(
                resolvedStudentId,
                resolvedName,
                entranceYear,
                graduationAbeekYear,
                department.name(),
                department.abeekCode(),
                uniqueEnrollments
        );

        return AbeekTranscriptEvaluationResponse.builder()
                .studentId(student.getStudentId())
                .studentNo(student.getStudentId())
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

    /** 같은 과목·연도·학기 중복은 유니크 제약 위반을 막기 위해 마지막 것만 남긴다. */
    private List<StudentEnrollment> dedupeEnrollments(List<StudentEnrollment> enrollments) {
        Map<String, StudentEnrollment> unique = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : enrollments) {
            String key = enrollment.getCourseMaster().getCourseCode()
                    + "|" + enrollment.getTakenYear()
                    + "|" + enrollment.getTakenSemester();
            unique.put(key, enrollment);
        }
        return new ArrayList<>(unique.values());
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

        // clear()+즉시 insert 는 유니크 제약(student, course, year, semester)에서
        // DELETE 전에 INSERT 되어 재업로드 시 500이 난다. 먼저 DB에서 지우고 flush.
        student.getEnrollments().clear();
        AbeekStudent saved = abeekStudentRepository.saveAndFlush(student);
        if (saved.getId() != null) {
            studentEnrollmentRepository.deleteByStudent_Id(saved.getId());
            studentEnrollmentRepository.flush();
            saved.getEnrollments().clear();
        }

        for (StudentEnrollment enrollment : enrollments) {
            saved.addEnrollment(enrollment);
        }
        return abeekStudentRepository.saveAndFlush(saved);
    }

    private List<CompletedCourseUploadRowDto> parseTranscriptFile(MultipartFile file) {
        try {
            List<CompletedCourseUploadRowDto> rows = transcriptExcelParser.parse(file);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException(
                        "성적표에서 이수 과목을 찾지 못했습니다. 파일 형식/시트명(기이수성적)을 확인하세요.");
            }
            return rows;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalArgumentException(
                    "성적표 엑셀 파싱 실패: " + (cause.getMessage() == null ? ex.getMessage() : cause.getMessage()),
                    ex
            );
        }
    }

    private Student resolveAppStudent(Long studentDbId, String studentNo) {
        if (studentDbId != null) {
            return transcriptStorageService.getStudentOrThrow(studentDbId);
        }
        if (studentNo != null && !studentNo.isBlank()) {
            return transcriptStorageService.getStudentByStudentNoOrThrow(studentNo);
        }
        throw new IllegalArgumentException(
                "학생 식별자가 없습니다. studentDbId(DB PK) 또는 studentNo(학번) 중 하나를 전달하세요.");
    }

    private AbeekDepartmentCatalog.DepartmentInfo resolveDepartment(
            List<CompletedCourseUploadRowDto> rows,
            String departmentCodeOverride,
            String preferredDepartmentName
    ) {
        if (departmentCodeOverride != null && !departmentCodeOverride.isBlank()) {
            return departmentCatalog.findByAbeekCode(departmentCodeOverride)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "지원하지 않는 ABEEK 학과코드입니다: " + departmentCodeOverride.trim()));
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

        if (!counts.isEmpty()) {
            String topSejongCode = counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElseThrow();
            return departmentCatalog.findBySejongCode(topSejongCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "개설학과코드로 ABEEK 학과를 매핑하지 못했습니다: " + topSejongCode
                                    + ". departmentCode를 직접 지정하세요."));
        }

        if (preferredDepartmentName != null && !preferredDepartmentName.isBlank()) {
            return departmentCatalog.findByDepartmentName(preferredDepartmentName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "학생 전공명으로 ABEEK 학과를 추론하지 못했습니다: " + preferredDepartmentName
                                    + ". departmentCode를 직접 지정하세요."));
        }

        throw new IllegalArgumentException(
                "학과를 추론할 수 없습니다. 성적표 개설학과코드가 없거나, 학생 전공/departmentCode가 비어 있습니다.");
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
                .orElseThrow(() -> new IllegalArgumentException(
                        "입학년도를 추론할 수 없습니다. entranceYear를 지정하세요."));
    }

    private int inferGraduationAbeekYear(List<CompletedCourseUploadRowDto> rows) {
        return rows.stream()
                .map(row -> parseInt(row.year(), Integer.MIN_VALUE))
                .filter(year -> year != Integer.MIN_VALUE)
                .max(Integer::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "졸업 ABEEK 연도를 추론할 수 없습니다. graduationAbeekYear를 지정하세요."));
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
