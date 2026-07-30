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
import com.example.congraduation.service.transcript.TranscriptStorageService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlannedCourseService {

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

    @Transactional(readOnly = true)
    public PlannedCourseListResponseDto getPlannedCourses(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto addPlannedCourse(Long studentId, PlannedCourseRequestDto request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        PlannedSemester plannedSemester = resolvePlannedSemester(studentId, request);

        PlannedCourse plannedCourse = PlannedCourse.create(
                student,
                plannedSemester,
                plannedSemester.getGradeYear(),
                plannedSemester.getSemester(),
                requireText(request.courseCode(), "학수번호"),
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
    public PlannedCourseListResponseDto addNextPlannedSemesters(Long studentId, int count) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        int requestedCount = count <= 0 ? 1 : count;

        List<PlannedSemester> existingSemesters = plannedSemesterRepository.findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(studentId);
        int lastStep = Math.max(resolveLastCompletedStep(student), resolveLastPlannedStep(existingSemesters));
        for (int i = 1; i <= requestedCount; i++) {
            int nextStep = lastStep + i;
            int gradeYear = ((nextStep - 1) / 2) + 1;
            int semester = ((nextStep - 1) % 2) + 1;
            if (gradeYear > 4) {
                throw new IllegalArgumentException("추가 가능한 학기는 4학년 2학기까지입니다.");
            }
            plannedSemesterRepository.findByStudentIdAndGradeYearAndSemester(studentId, gradeYear, semester)
                    .orElseGet(() -> plannedSemesterRepository.save(PlannedSemester.create(student, gradeYear, semester)));
        }

        return buildResponse(student, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional(readOnly = true)
    public List<CompletedCourseUploadRowDto> getProjectedRows(Long studentId) {
        return plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId).stream()
                .map(course -> new CompletedCourseUploadRowDto(
                        String.valueOf(course.getTargetYear()),
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

    private PlannedCourseListResponseDto buildResponse(Student student, List<PlannedCourse> courses) {
        Map<String, SemesterAccumulator> semesterMap = new LinkedHashMap<>();
        BigDecimal totalCredits = BigDecimal.ZERO;
        List<PlannedSemester> plannedSemesters = plannedSemesterRepository
                .findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(student.getId());

        for (PlannedSemester plannedSemester : plannedSemesters) {
            semesterMap.put(
                    toSemesterKey(plannedSemester.getGradeYear(), plannedSemester.getSemester()),
                    new SemesterAccumulator(plannedSemester.getId(), plannedSemester.getGradeYear(), plannedSemester.getSemester())
            );
        }

        for (PlannedCourse course : courses) {
            String key = toSemesterKey(course.getGradeYear(), course.getSemester());
            SemesterAccumulator accumulator = semesterMap.computeIfAbsent(
                    key,
                    ignored -> new SemesterAccumulator(course.getPlannedSemesterId(), course.getGradeYear(), course.getSemester())
            );
            accumulator.courses.add(PlannedCourseResponseDto.from(course));

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

        return new PlannedCourseListResponseDto(
                student.getId(),
                formatSemesterLabel(resolveLastCompletedStep(student)),
                formatDecimal(totalCredits),
                semesters
        );
    }

    private PlannedSemester resolvePlannedSemester(Long studentId, PlannedCourseRequestDto request) {
        if (request.plannedSemesterId() != null) {
            return plannedSemesterRepository.findByIdAndStudentId(request.plannedSemesterId(), studentId)
                    .orElseThrow(() -> new IllegalArgumentException("계획 학기를 찾을 수 없습니다."));
        }

        Integer gradeYear = requireGradeYear(request.gradeYear());
        Integer semester = requireSemester(request.semester());
        return plannedSemesterRepository.findByStudentIdAndGradeYearAndSemester(studentId, gradeYear, semester)
                .orElseThrow(() -> new IllegalArgumentException("먼저 해당 빈 학기를 추가해주세요."));
    }

    private Integer requireGradeYear(Integer gradeYear) {
        if (gradeYear == null || gradeYear < 1 || gradeYear > 4) {
            throw new IllegalArgumentException("학년은 1~4 사이만 가능합니다.");
        }
        return gradeYear;
    }

    private Integer requireSemester(Integer semester) {
        if (semester == null || (semester != 1 && semester != 2)) {
            throw new IllegalArgumentException("학기는 1 또는 2만 가능합니다.");
        }
        return semester;
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

    private String defaultText(String value) {
        return defaultText(value, "");
    }

    private String defaultText(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int resolveLastCompletedStep(Student student) {
        if (!transcriptStorageService.hasTranscript(student.getId())) {
            return Math.max(0, ((student.getGradeLevel() == null ? 1 : student.getGradeLevel()) - 1) * 2);
        }

        return transcriptStorageService.getLatestTranscriptRows(student.getId()).stream()
                .map(row -> toAcademicStep(student, row))
                .filter(step -> step > 0)
                .max(Comparator.naturalOrder())
                .orElse(Math.max(0, ((student.getGradeLevel() == null ? 1 : student.getGradeLevel()) - 1) * 2));
    }

    private int resolveLastPlannedStep(List<PlannedSemester> semesters) {
        return semesters.stream()
                .map(semester -> toAcademicStep(semester.getGradeYear(), semester.getSemester()))
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    private int toAcademicStep(Student student, CompletedCourseUploadRowDto row) {
        Integer year = parseInt(row.year());
        if (year == null) {
            return 0;
        }

        int semesterIndex = toRegularSemesterIndex(row.semester());
        if (semesterIndex == 0) {
            return 0;
        }

        Integer admissionYear = student.getAdmissionYear();
        if (admissionYear == null || year < admissionYear) {
            return 0;
        }

        return ((year - admissionYear) * 2) + semesterIndex;
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

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toRegularSemesterIndex(String semesterText) {
        if (semesterText == null) {
            return 0;
        }
        String normalized = semesterText.trim();
        if (normalized.contains("2")) {
            return 2;
        }
        if (normalized.contains("1") || normalized.contains("여름")) {
            return 1;
        }
        return 0;
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
