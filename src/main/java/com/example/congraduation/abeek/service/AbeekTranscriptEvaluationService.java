package com.example.congraduation.abeek.service;

import com.example.congraduation.abeek.domain.AbeekStudent;
import com.example.congraduation.abeek.domain.CourseMaster;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.StudentEnrollment;
import com.example.congraduation.abeek.dto.AbeekEvaluationResponse;
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

        // 타 학과 OCR 마스터와 과목명이 겹치면 putIfAbsent로 잘못 매칭되므로
        // 소속 학과 커리큘럼(및 CSE GEN/BSM/MAJ 시드)만 인덱싱한다.
        Map<String, CourseMaster> mastersByNormalizedName = buildDepartmentMasterIndex(
                department.abeekCode(), entranceYear, graduationAbeekYear);
        List<MatchedCourseDto> matches = new ArrayList<>();
        List<UnmatchedCourseDto> unmatched = new ArrayList<>();
        List<StudentEnrollment> enrollments = new ArrayList<>();

        for (CompletedCourseUploadRowDto row : rows) {
            Optional<CourseMaster> matched = matchCourse(row.courseName(), row.category(), mastersByNormalizedName);
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
                    department.abeekCode(),
                    master.getCourseCode(),
                    takenYear,
                    entranceYear,
                    graduationAbeekYear);

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

        AbeekEvaluationResponse evaluation = abeekEvaluationService.evaluate(student.getStudentId());

        return AbeekTranscriptEvaluationResponse.builder()
                .studentId(student.getStudentId())
                .studentNo(student.getStudentId())
                .studentName(student.getName())
                .inferredSejongDepartmentCode(department.sejongCode())
                .departmentCode(department.abeekCode())
                .departmentName(department.name())
                .entranceYear(entranceYear)
                .graduationAbeekYear(evaluation.getGraduationAbeekYear())
                .expectedGraduationYear(evaluation.getExpectedGraduationYear())
                .graduationAbeekBasisLabel(evaluation.getGraduationAbeekBasisLabel())
                .totalCourses(rows.size())
                .matchedCourses(matches.size())
                .unmatchedCourses(unmatched.size())
                .matches(matches)
                .unmatched(unmatched)
                .evaluation(evaluation)
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
        // "전공기초"는 BSM/기초 쪽이라 전필·전선으로 보지 않는다.
        if (value.contains("전공기초") || value.contains("기교") || value.contains("BSM")
                || value.contains("기초과학")) {
            return false;
        }
        return value.contains("전필") || value.contains("전선")
                || value.contains("전공필수") || value.contains("전공선택");
    }

    private boolean isBsmLike(String category) {
        if (category == null) {
            return false;
        }
        String value = category.replace(" ", "");
        return value.contains("전공기초") || value.contains("기교") || value.contains("BSM")
                || value.contains("기초과학");
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
        // 기이수 마지막 수강 학기(1학기/2학기/계절학기)의 연도 = 졸업 ABEEK 적용 연도
        return rows.stream()
                .map(row -> {
                    int year = parseInt(row.year(), Integer.MIN_VALUE);
                    if (year == Integer.MIN_VALUE) {
                        return null;
                    }
                    return new int[]{year, semesterOrder(row.semester())};
                })
                .filter(java.util.Objects::nonNull)
                .max(Comparator
                        .comparingInt((int[] t) -> t[0])
                        .thenComparingInt(t -> t[1]))
                .map(t -> t[0])
                .orElseThrow(() -> new IllegalArgumentException(
                        "졸업 ABEEK 연도를 추론할 수 없습니다. 기이수성적의 수강 연도/학기를 확인하세요."));
    }

    /**
     * 학기 순서: 1학기 &lt; 여름 &lt; 2학기 &lt; 겨울.
     * 마지막 학기가 1학기든 2학기든 그 해의 졸업 ABEEK 연도로 쓴다.
     */
    private int semesterOrder(String semester) {
        if (semester == null || semester.isBlank()) {
            return 1;
        }
        String value = semester.replaceAll("\\s+", "");
        if (value.contains("겨울")) {
            return 4;
        }
        if (value.contains("여름")) {
            return 2;
        }
        Matcher matcher = SEMESTER_PATTERN.matcher(value);
        if (matcher.find()) {
            int n = Integer.parseInt(matcher.group(1));
            if (n >= 2) {
                return 3;
            }
            return 1;
        }
        return 1;
    }

    /**
     * 소속 학과 커리큘럼을 우선 인덱싱하고, CSE 시드 코드(GEN_/BSM_/MAJ_)를 보조로 둔다.
     * 전역 CourseMaster(타 학과 OCR)는 제외해 동명 과목 오매칭을 막는다.
     */
    private Map<String, CourseMaster> buildDepartmentMasterIndex(
            String departmentCode,
            int entranceYear,
            int graduationAbeekYear
    ) {
        Map<String, CourseMaster> index = new LinkedHashMap<>();
        List<Integer> years = new ArrayList<>();
        years.add(entranceYear);
        if (graduationAbeekYear != entranceYear) {
            years.add(graduationAbeekYear);
        }
        for (int y = 2020; y <= 2026; y++) {
            if (!years.contains(y)) {
                years.add(y);
            }
        }

        for (int year : years) {
            for (CurriculumCourse cc : curriculumCourseRepository
                    .findAllWithMasterByDepartmentCodeAndYear(departmentCode, year)) {
                putMasterAliases(index, cc.getCourseMaster());
            }
        }

        for (CourseMaster master : courseMasterRepository.findAll()) {
            if (isAbeekSeedCourseCode(master.getCourseCode())) {
                putMasterAliases(index, master);
            }
        }
        return index;
    }

    private boolean isAbeekSeedCourseCode(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            return false;
        }
        return courseCode.startsWith("GEN_")
                || courseCode.startsWith("BSM_")
                || courseCode.startsWith("MAJ_");
    }

    private void putMasterAliases(Map<String, CourseMaster> index, CourseMaster master) {
        if (master == null || master.getName() == null) {
            return;
        }
        putPreferMajor(index, normalizeCourseName(master.getName()), master);
        String withoutParen = normalizeCourseName(master.getName().replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            putPreferMajor(index, withoutParen, master);
        }
        String withoutHyphen = normalizeCourseName(master.getName()).replace("-", "");
        if (!withoutHyphen.isBlank()) {
            putPreferMajor(index, withoutHyphen, master);
        }
        if ("GEN_ADV_PROG_P".equals(master.getCourseCode())) {
            // 성적표/개설명 변형: 입문-P, 이해-P
            putPreferMajor(index, normalizeCourseName("고급프로그래밍입문P"), master);
            putPreferMajor(index, normalizeCourseName("고급프로그래밍입문"), master);
            putPreferMajor(index, normalizeCourseName("고급프로그래밍이해-P"), master);
            putPreferMajor(index, normalizeCourseName("고급프로그래밍이해P"), master);
            putPreferMajor(index, normalizeCourseName("고급프로그래밍이해"), master);
        }
    }

    /** 동명일 때 MAJOR를 우선하되, 이미 BSM으로 잡힌 이름은 덮어쓰지 않는다. */
    private void putPreferMajor(Map<String, CourseMaster> index, String key, CourseMaster master) {
        if (key == null || key.isBlank()) {
            return;
        }
        CourseMaster existing = index.get(key);
        if (existing == null) {
            index.put(key, master);
            return;
        }
        if (existing.getCategory() == com.example.congraduation.abeek.domain.enums.CourseCategory.BSM) {
            return;
        }
        if (existing.getCategory() != com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR
                && master.getCategory() == com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR) {
            index.put(key, master);
        }
    }

    private Optional<CourseMaster> matchCourse(
            String transcriptName,
            String transcriptCategory,
            Map<String, CourseMaster> index
    ) {
        if (transcriptName == null || transcriptName.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeCourseName(transcriptName);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        boolean majorLike = isMajorLike(transcriptCategory);
        boolean bsmLike = isBsmLike(transcriptCategory);

        CourseMaster exact = index.get(normalized);
        if (exact != null) {
            if (majorLike && exact.getCategory() != com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR) {
                Optional<CourseMaster> majorExact = findByNormalizedNameAndCategory(
                        normalized, com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR, index);
                if (majorExact.isPresent()) {
                    return majorExact;
                }
            }
            if (bsmLike && exact.getCategory() != com.example.congraduation.abeek.domain.enums.CourseCategory.BSM) {
                Optional<CourseMaster> bsmExact = findByNormalizedNameAndCategory(
                        normalized, com.example.congraduation.abeek.domain.enums.CourseCategory.BSM, index);
                if (bsmExact.isPresent()) {
                    return bsmExact;
                }
            }
            if (!majorLike || exact.getCategory() == com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR) {
                return Optional.of(exact);
            }
        }

        String withoutParen = normalizeCourseName(transcriptName.replaceAll("\\([^)]*\\)", ""));
        if (!withoutParen.isBlank()) {
            CourseMaster byParen = index.get(withoutParen);
            if (byParen != null) {
                if (majorLike && byParen.getCategory() != com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR) {
                    Optional<CourseMaster> majorParen = findByNormalizedNameAndCategory(
                            withoutParen, com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR, index);
                    if (majorParen.isPresent()) {
                        return majorParen;
                    }
                }
                if (bsmLike && byParen.getCategory() != com.example.congraduation.abeek.domain.enums.CourseCategory.BSM) {
                    Optional<CourseMaster> bsmParen = findByNormalizedNameAndCategory(
                            withoutParen, com.example.congraduation.abeek.domain.enums.CourseCategory.BSM, index);
                    if (bsmParen.isPresent()) {
                        return bsmParen;
                    }
                }
                if (!majorLike || byParen.getCategory() == com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR) {
                    return Optional.of(byParen);
                }
            }
        }

        // 전필/전선 별칭 (캡스톤 등) + 교양 별칭
        Optional<CourseMaster> byAlias = matchCourseAlias(normalized, withoutParen, index, majorLike);
        if (byAlias.isPresent()) {
            return byAlias;
        }

        // 짧은 부분문자열(예: "프로그래밍")로 오매칭되지 않도록 길이 비율을 제한한다.
        final int minLen = 4;
        if (normalized.length() < minLen) {
            return Optional.empty();
        }
        return index.entrySet().stream()
                .filter(entry -> {
                    if (majorLike && entry.getValue().getCategory()
                            != com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR) {
                        return false;
                    }
                    if (bsmLike && entry.getValue().getCategory()
                            != com.example.congraduation.abeek.domain.enums.CourseCategory.BSM) {
                        return false;
                    }
                    String key = entry.getKey();
                    if (key.length() < minLen) {
                        return false;
                    }
                    if (!(key.contains(normalized) || normalized.contains(key))) {
                        return false;
                    }
                    int shorter = Math.min(key.length(), normalized.length());
                    int longer = Math.max(key.length(), normalized.length());
                    return shorter * 10 >= longer * 6; // >= 60%
                })
                .min(Comparator.comparingInt(entry -> Math.abs(entry.getKey().length() - normalized.length())))
                .map(Map.Entry::getValue);
    }

    private Optional<CourseMaster> findByNormalizedNameAndCategory(
            String normalized,
            com.example.congraduation.abeek.domain.enums.CourseCategory category,
            Map<String, CourseMaster> index
    ) {
        return index.values().stream()
                .filter(m -> m.getCategory() == category)
                .filter(m -> {
                    String n = normalizeCourseName(m.getName());
                    String wp = normalizeCourseName(m.getName().replaceAll("\\([^)]*\\)", ""));
                    return n.equals(normalized) || wp.equals(normalized);
                })
                .findFirst();
    }

    private Optional<CourseMaster> matchCourseAlias(
            String normalized,
            String withoutParen,
            Map<String, CourseMaster> index,
            boolean majorLike
    ) {
        String probe = withoutParen != null && !withoutParen.isBlank() ? withoutParen : normalized;
        if (isAdvancedProgrammingIntroName(probe)) {
            CourseMaster adv = index.get(normalizeCourseName("고급프로그래밍입문-P"));
            if (adv == null) {
                adv = index.get(normalizeCourseName("고급프로그래밍이해-P"));
            }
            if (adv == null) {
                adv = index.values().stream()
                        .filter(m -> "GEN_ADV_PROG_P".equals(m.getCourseCode())
                                || isAdvancedProgrammingIntroName(normalizeCourseName(m.getName())))
                        .findFirst()
                        .orElse(null);
            }
            if (adv != null) {
                return Optional.of(adv);
            }
        }
        if (!majorLike) {
            return Optional.empty();
        }
        return matchMajorAlias(normalized, withoutParen, index);
    }

    /** 고급프로그래밍입문-P / 고급프로그래밍이해-P 동일 과목 취급 */
    private boolean isAdvancedProgrammingIntroName(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String value = normalized.replace("-", "");
        return value.contains("고급프로그래밍입문") || value.contains("고급프로그래밍이해");
    }

    private Optional<CourseMaster> matchMajorAlias(
            String normalized,
            String withoutParen,
            Map<String, CourseMaster> index
    ) {
        String probe = withoutParen != null && !withoutParen.isBlank() ? withoutParen : normalized;
        if (probe.contains("capstone") || probe.contains("캡스톤") || probe.contains("산학협력프로젝트")) {
            CourseMaster capstone = index.values().stream()
                    .filter(m -> m.getCategory() == com.example.congraduation.abeek.domain.enums.CourseCategory.MAJOR)
                    .filter(m -> {
                        String n = normalizeCourseName(m.getName());
                        return n.contains("capstone") || n.contains("캡스톤");
                    })
                    .findFirst()
                    .orElse(null);
            if (capstone != null) {
                return Optional.of(capstone);
            }
        }
        if (probe.contains("공학설계기초") || probe.equals("산학프로젝트입문")) {
            CourseMaster basic = index.get(normalizeCourseName("공학설계기초(산학프로젝트입문)"));
            if (basic != null) {
                return Optional.of(basic);
            }
        }
        return Optional.empty();
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
            int entranceYear,
            int graduationAbeekYear
    ) {
        List<Integer> years = new ArrayList<>();
        years.add(takenYear);
        years.add(entranceYear);
        years.add(graduationAbeekYear);
        for (int y = 2020; y <= 2026; y++) {
            if (!years.contains(y)) {
                years.add(y);
            }
        }
        for (int year : years) {
            Optional<CurriculumCourse> course = curriculumCourseRepository
                    .findByCurriculumYearAndDepartmentCodeAndCourseMaster_CourseCode(
                            year, departmentCode, courseCode);
            if (course.isPresent()) {
                return course.get().getDesignCredits();
            }
        }
        return 0.0;
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
