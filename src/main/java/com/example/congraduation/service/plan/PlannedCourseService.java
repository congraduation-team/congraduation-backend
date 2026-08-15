package com.example.congraduation.service.plan;

import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.plan.PlannedCourse;
import com.example.congraduation.domain.plan.PlannedSemester;
import com.example.congraduation.dto.plan.PlannedCourseExpectedGradeRequestDto;
import com.example.congraduation.dto.plan.PlannedCourseGradePolicy;
import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.plan.PlannedCourseRequestDto;
import com.example.congraduation.dto.plan.PlannedCourseResponseDto;
import com.example.congraduation.dto.plan.PlannedSemesterSummaryDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.repository.plan.PlannedCourseRepository;
import com.example.congraduation.repository.plan.PlannedSemesterRepository;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.transcript.TranscriptStandingMapper;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlannedCourseService {

    /** 계획 학기 상한(학년). 초과학년 계획용 여유. */
    private static final int MAX_PLAN_GRADE_YEAR = 8;

    /** 표준 졸업 로드맵 마지막 순번 (4-2). 남은 학기 카드는 여기까지 채운다. */
    private static final int STANDARD_GRADUATION_STEP = 8;

    private final StudentRepository studentRepository;
    private final PlannedCourseRepository plannedCourseRepository;
    private final PlannedSemesterRepository plannedSemesterRepository;
    private final TranscriptStorageService transcriptStorageService;

    public PlannedCourseService(
            StudentRepository studentRepository,
            PlannedCourseRepository plannedCourseRepository,
            PlannedSemesterRepository plannedSemesterRepository,
            TranscriptStorageService transcriptStorageService
    ) {
        this.studentRepository = studentRepository;
        this.plannedCourseRepository = plannedCourseRepository;
        this.plannedSemesterRepository = plannedSemesterRepository;
        this.transcriptStorageService = transcriptStorageService;
    }

    @Transactional
    public PlannedCourseListResponseDto getPlannedCourses(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        // 성적표가 있는 경우 수강계획 화면 진입 시에만 빈 학기 카드를 확보한다.
        // 졸업요건(evaluate)은 listPlannedCourses(readOnly)를 쓰므로 여기서 write 하지 않는다.
        if (transcriptStorageService.hasTranscript(studentId)) {
            ensureRemainingSemestersThroughGraduation(student);
        }
        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    /**
     * 졸업요건 등 read-only 조회용. 계획 학기를 새로 만들지 않는다.
     */
    @Transactional(readOnly = true)
    public PlannedCourseListResponseDto listPlannedCourses(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        return buildResponse(
                student,
                plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId)
        );
    }

    @Transactional
    public PlannedCourseListResponseDto addPlannedCourse(Long studentId, PlannedCourseRequestDto request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        PlannedSemester plannedSemester = resolvePlannedSemester(studentId, request);
        String courseCode = requireText(request.courseCode(), "학수번호");
        validateNoDuplicatePlannedCourse(studentId, courseCode);
        validateRetakeEligibility(studentId, courseCode);

        PlannedCourse plannedCourse = PlannedCourse.create(
                student,
                plannedSemester,
                plannedSemester.getGradeYear(),
                plannedSemester.getSemester(),
                courseCode,
                requireText(request.courseName(), "교과목명"),
                normalizeText(request.category()),
                requireCredit(request.credit()),
                normalizeExpectedGrade(request.expectedGrade())
        );
        plannedCourseRepository.save(plannedCourse);

        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto updateExpectedGrade(
            Long studentId,
            Long plannedCourseId,
            PlannedCourseExpectedGradeRequestDto request
    ) {
        if (!studentRepository.existsById(studentId)) {
            throw new IllegalArgumentException("학생을 찾을 수 없습니다.");
        }

        PlannedCourse plannedCourse = plannedCourseRepository.findByIdAndStudentId(plannedCourseId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("계획 과목을 찾을 수 없습니다."));
        plannedCourse.updateExpectedGrade(normalizeExpectedGrade(request.expectedGrade()));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto deletePlannedCourse(Long studentId, Long plannedCourseId) {
        if (!studentRepository.existsById(studentId)) {
            throw new IllegalArgumentException("학생을 찾을 수 없습니다.");
        }

        plannedCourseRepository.deleteByIdAndStudentId(plannedCourseId, studentId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto deletePlannedSemester(Long studentId, Long plannedSemesterId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        plannedSemesterRepository.findByIdAndStudentId(plannedSemesterId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("계획 학기를 찾을 수 없습니다."));

        plannedCourseRepository.deleteAllByPlannedSemester_IdAndStudentId(plannedSemesterId, studentId);
        plannedSemesterRepository.deleteByIdAndStudentId(plannedSemesterId, studentId);

        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto addNextPlannedSemesters(Long studentId, int count) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        // FE는 보통 count=1만 보냄. 기본은 마지막 이수 다음~4-2를 한 번에 채운다.
        if (count <= 1) {
            ensureRemainingSemestersThroughGraduation(student);
            return buildResponse(
                    student,
                    plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId)
            );
        }

        List<PlannedSemester> existingSemesters = plannedSemesterRepository
                .findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(studentId);
        int lastStep = Math.max(resolveLastCompletedStep(student), resolveLastPlannedStep(existingSemesters));
        for (int i = 1; i <= count; i++) {
            int nextStep = lastStep + i;
            int gradeYear = ((nextStep - 1) / 2) + 1;
            int semester = ((nextStep - 1) % 2) + 1;
            if (gradeYear > MAX_PLAN_GRADE_YEAR) {
                throw new IllegalArgumentException(
                        "추가 가능한 학기는 " + MAX_PLAN_GRADE_YEAR + "학년 2학기까지입니다.");
            }
            ensurePlannedSemester(student, gradeYear, semester);
        }

        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    /**
     * 기이수 순번 다음부터 표준 졸업(4-2)까지 빈 계획 학기를 모두 만든다.
     * 예: 마지막 이수 3-1 → 3-2, 4-1, 4-2 / 마지막 이수 1-1 → 1-2 … 4-2
     */
    private void ensureRemainingSemestersThroughGraduation(Student student) {
        int lastCompletedStep = resolveLastCompletedStep(student);
        for (int step = lastCompletedStep + 1; step <= STANDARD_GRADUATION_STEP; step++) {
            int gradeYear = ((step - 1) / 2) + 1;
            int semester = ((step - 1) % 2) + 1;
            ensurePlannedSemester(student, gradeYear, semester);
        }
    }

    private void ensurePlannedSemester(Student student, int gradeYear, int semester) {
        findCanonicalPlannedSemester(student.getId(), gradeYear, semester)
                .orElseGet(() -> createPlannedSemester(student, gradeYear, semester));
    }

    private PlannedSemester createPlannedSemester(Student student, int gradeYear, int semester) {
        try {
            return plannedSemesterRepository.save(PlannedSemester.create(student, gradeYear, semester));
        } catch (DataIntegrityViolationException ex) {
            return findCanonicalPlannedSemester(student.getId(), gradeYear, semester)
                    .orElseThrow(() -> ex);
        }
    }

    private Optional<PlannedSemester> findCanonicalPlannedSemester(Long studentId, int gradeYear, int semester) {
        return plannedSemesterRepository
                .findTopByStudentIdAndGradeYearAndSemesterOrderByCreatedAtAscIdAsc(studentId, gradeYear, semester);
    }

    @Transactional(readOnly = true)
    public List<CompletedCourseUploadRowDto> getProjectedRows(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        int lastCompletedStep = resolveLastCompletedStep(student);

        return plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId).stream()
                .filter(course -> toAcademicStep(course.getGradeYear(), course.getSemester()) > lastCompletedStep)
                .map(course -> new CompletedCourseUploadRowDto(
                        String.valueOf(resolveProjectedCalendarYear(student, course)),
                        course.getTargetSemester() + "학기",
                        course.getCourseCode(),
                        course.getCourseName(),
                        course.getCategory(),
                        course.getCredit(),
                        PlannedCourseGradePolicy.toEvaluationMethod(course.getExpectedGrade()),
                        defaultText(course.getExpectedGrade()),
                        defaultText(PlannedCourseGradePolicy.toGradePoint(course.getExpectedGrade()), "0")
                ))
                .toList();
    }

    private int resolveProjectedCalendarYear(Student student, PlannedCourse course) {
        Integer admissionYear = student.getAdmissionYear();
        if (admissionYear == null) {
            return course.getTargetYear();
        }
        return admissionYear + course.getGradeYear() - 1;
    }

    private PlannedCourseListResponseDto buildResponse(Student student, List<PlannedCourse> courses) {
        Map<String, SemesterAccumulator> semesterMap = new LinkedHashMap<>();
        BigDecimal totalCredits = BigDecimal.ZERO;
        Map<String, CompletedCourseUploadRowDto> transcriptCourseMap = resolveTranscriptCourseMap(student.getId());
        int lastCompletedStep = resolveLastCompletedStep(student);
        List<PlannedSemester> plannedSemesters = plannedSemesterRepository
                .findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(student.getId());

        for (PlannedSemester plannedSemester : plannedSemesters) {
            // 이미 이수한 학기(마지막 이수 학기 포함)는 졸업 시뮬레이션/계획 카드에 내리지 않는다.
            if (toAcademicStep(plannedSemester.getGradeYear(), plannedSemester.getSemester()) <= lastCompletedStep) {
                continue;
            }
            semesterMap.putIfAbsent(
                    toSemesterKey(plannedSemester.getGradeYear(), plannedSemester.getSemester()),
                    new SemesterAccumulator(plannedSemester.getId(), plannedSemester.getGradeYear(), plannedSemester.getSemester())
            );
        }

        for (PlannedCourse course : courses) {
            if (toAcademicStep(course.getGradeYear(), course.getSemester()) <= lastCompletedStep) {
                continue;
            }
            String key = toSemesterKey(course.getGradeYear(), course.getSemester());
            SemesterAccumulator accumulator = semesterMap.computeIfAbsent(
                    key,
                    ignored -> new SemesterAccumulator(course.getPlannedSemesterId(), course.getGradeYear(), course.getSemester())
            );
            CompletedCourseUploadRowDto previousCourse = transcriptCourseMap.get(normalizeCourseCode(course.getCourseCode()));
            accumulator.courses.add(PlannedCourseResponseDto.from(
                    course,
                    previousCourse != null,
                    previousCourse == null ? null : defaultText(previousCourse.grade(), null),
                    previousCourse == null ? null : defaultText(previousCourse.gradePoint(), "0")
            ));

            BigDecimal credit = toDecimal(course.getCredit());
            accumulator.totalCredits = accumulator.totalCredits.add(credit);
            totalCredits = totalCredits.add(credit);
        }

        List<PlannedSemesterSummaryDto> semesters = semesterMap.values().stream()
                .map(accumulator -> new PlannedSemesterSummaryDto(
                        accumulator.plannedSemesterId,
                        accumulator.gradeYear,
                        accumulator.semester,
                        formatDecimal(accumulator.totalCredits),
                        accumulator.courses.isEmpty(),
                        List.copyOf(accumulator.courses)
                ))
                .toList();

        StandingSnapshot standing = resolveStandingSnapshot(student);
        return new PlannedCourseListResponseDto(
                student.getId(),
                standing.termKey(),
                standing.takenYear(),
                standing.takenSemester(),
                standing.gradeYear(),
                standing.overStanding(),
                formatDecimal(totalCredits),
                semesters
        );
    }

    private StandingSnapshot resolveStandingSnapshot(Student student) {
        int step = resolveLastCompletedStep(student);
        String termKey = formatSemesterLabel(step);
        Integer gradeYear = null;
        boolean overStanding = false;
        if (termKey != null) {
            String[] parts = termKey.split("-");
            gradeYear = Integer.parseInt(parts[0]);
            overStanding = gradeYear > 4;
        }

        String takenYear = null;
        String takenSemester = null;
        if (transcriptStorageService.hasTranscript(student.getId())) {
            List<CompletedCourseUploadRowDto> rows =
                    transcriptStorageService.getLatestTranscriptRows(student.getId());
            TranscriptStandingMapper mapper =
                    TranscriptStandingMapper.fromRows(rows, student.getAdmissionYear());
            int bestStep = 0;
            for (CompletedCourseUploadRowDto row : rows) {
                int rowStep = mapper.resolveStep(row.year(), row.semester());
                // official 이수 학기가 있으면 그 순번 이하만 마지막 이수 달력학기로 본다.
                if (rowStep <= 0 || rowStep > step || rowStep <= bestStep) {
                    continue;
                }
                bestStep = rowStep;
                takenYear = row.year();
                takenSemester = row.semester();
            }
        }

        return new StandingSnapshot(termKey, takenYear, takenSemester, gradeYear, overStanding);
    }

    private record StandingSnapshot(
            String termKey,
            String takenYear,
            String takenSemester,
            Integer gradeYear,
            boolean overStanding
    ) {
    }

    private PlannedSemester resolvePlannedSemester(Long studentId, PlannedCourseRequestDto request) {
        if (request.plannedSemesterId() != null) {
            return plannedSemesterRepository.findByIdAndStudentId(request.plannedSemesterId(), studentId)
                    .orElseThrow(() -> new IllegalArgumentException("계획 학기를 찾을 수 없습니다."));
        }

        Integer gradeYear = requireGradeYear(request.gradeYear());
        Integer semester = requireSemester(request.semester());
        return findCanonicalPlannedSemester(studentId, gradeYear, semester)
                .orElseThrow(() -> new IllegalArgumentException("먼저 해당 빈 학기를 추가해주세요."));
    }

    private Integer requireGradeYear(Integer gradeYear) {
        if (gradeYear == null || gradeYear < 1 || gradeYear > MAX_PLAN_GRADE_YEAR) {
            throw new IllegalArgumentException("학년은 1~" + MAX_PLAN_GRADE_YEAR + " 사이만 가능합니다.");
        }
        return gradeYear;
    }

    private Integer requireSemester(Integer semester) {
        if (semester == null || (semester != 1 && semester != 2)) {
            throw new IllegalArgumentException("학기는 1 또는 2만 가능합니다.");
        }
        return semester;
    }

    private void validateNoDuplicatePlannedCourse(Long studentId, String courseCode) {
        String normalizedCourseCode = normalizeCourseCode(courseCode);
        boolean duplicated = plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId).stream()
                .map(PlannedCourse::getCourseCode)
                .map(this::normalizeCourseCode)
                .anyMatch(normalizedCourseCode::equals);
        if (duplicated) {
            throw new IllegalArgumentException("이미 수강 계획에 추가한 학수번호입니다.");
        }
    }

    private void validateRetakeEligibility(Long studentId, String courseCode) {
        CompletedCourseUploadRowDto completedCourse = resolveTranscriptCourseMap(studentId).get(normalizeCourseCode(courseCode));
        if (completedCourse == null) {
            return;
        }
        if (isRetakeBlocked(completedCourse)) {
            throw new IllegalArgumentException("기존 성적이 B0 이상인 과목은 재수강 계획에 추가할 수 없습니다.");
        }
    }

    private String requireText(String value, String fieldName) {
        String normalized = normalizeText(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + "을(를) 입력해주세요.");
        }
        return normalized;
    }

    private String requireCredit(String credit) {
        String normalized = requireText(credit, "학점");
        toDecimal(normalized);
        return normalized;
    }

    private String normalizeExpectedGrade(String expectedGrade) {
        String normalized = PlannedCourseGradePolicy.normalize(expectedGrade);
        if (!PlannedCourseGradePolicy.isSupported(normalized)) {
            throw new IllegalArgumentException("예상 성적은 A+, A0, B+, B0, C+, C0, D+, D0, F, P, NP만 가능합니다.");
        }
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private Map<String, CompletedCourseUploadRowDto> resolveTranscriptCourseMap(Long studentId) {
        Map<String, CompletedCourseUploadRowDto> courseMap = new LinkedHashMap<>();
        for (CompletedCourseUploadRowDto row : transcriptStorageService.getLatestTranscriptRows(studentId)) {
            String courseCode = normalizeCourseCode(row.courseCode());
            if (courseCode.isBlank()) {
                continue;
            }
            courseMap.put(courseCode, row);
        }
        return courseMap;
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

    private BigDecimal parseGradePoint(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String normalizeGrade(String grade) {
        return grade == null ? "" : grade.trim().toUpperCase();
    }

    private String normalizeCourseCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim();
    }

    private String defaultText(String value) {
        return defaultText(value, "");
    }

    private String defaultText(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int resolveLastCompletedStep(Student student) {
        // classic "이수 학기"가 있으면 최우선. 군E러닝 등으로 기이수 학기 라벨이 부풀어도 보정한다.
        Integer officialCompletedSemesters = student.getCompletedSemesterCount();
        if (officialCompletedSemesters != null && officialCompletedSemesters >= 0) {
            return officialCompletedSemesters;
        }

        int fallback = Math.max(0, ((student.getGradeLevel() == null ? 1 : student.getGradeLevel()) - 1) * 2);
        if (!transcriptStorageService.hasTranscript(student.getId())) {
            return fallback;
        }

        List<CompletedCourseUploadRowDto> rows =
                transcriptStorageService.getLatestTranscriptRows(student.getId());
        // 달력 상대학년(takenYear−admissionYear+1) 금지. 기이수 정규학기 순번만 사용.
        TranscriptStandingMapper standing =
                TranscriptStandingMapper.fromRows(rows, student.getAdmissionYear());
        return rows.stream()
                .mapToInt(row -> standing.resolveStep(row.year(), row.semester()))
                .filter(step -> step > 0)
                .max()
                .orElse(fallback);
    }

    private int resolveLastPlannedStep(List<PlannedSemester> semesters) {
        return semesters.stream()
                .map(semester -> toAcademicStep(semester.getGradeYear(), semester.getSemester()))
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    private int toAcademicStep(Integer gradeYear, Integer semester) {
        return ((gradeYear - 1) * 2) + semester;
    }

    private String toSemesterKey(Integer gradeYear, Integer semester) {
        return gradeYear + "-" + semester;
    }

    private String formatSemesterLabel(int step) {
        if (step <= 0) {
            return null;
        }
        int gradeYear = ((step - 1) / 2) + 1;
        int semester = ((step - 1) % 2) + 1;
        return gradeYear + "-" + semester;
    }

    private BigDecimal toDecimal(String value) {
        return new BigDecimal(value.trim());
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static final class SemesterAccumulator {
        private final Long plannedSemesterId;
        private final Integer gradeYear;
        private final Integer semester;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private final List<PlannedCourseResponseDto> courses = new ArrayList<>();

        private SemesterAccumulator(Long plannedSemesterId, Integer gradeYear, Integer semester) {
            this.plannedSemesterId = plannedSemesterId;
            this.gradeYear = gradeYear;
            this.semester = semester;
        }
    }
}
