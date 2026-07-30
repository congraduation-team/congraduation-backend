package com.example.congraduation.service.plan;

import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.plan.PlannedCourse;
import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.plan.PlannedCourseRequestDto;
import com.example.congraduation.dto.plan.PlannedCourseResponseDto;
import com.example.congraduation.dto.plan.PlannedSemesterSummaryDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.repository.plan.PlannedCourseRepository;
import com.example.congraduation.repository.student.StudentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlannedCourseService {

    private final StudentRepository studentRepository;
    private final PlannedCourseRepository plannedCourseRepository;

    public PlannedCourseService(
            StudentRepository studentRepository,
            PlannedCourseRepository plannedCourseRepository
    ) {
        this.studentRepository = studentRepository;
        this.plannedCourseRepository = plannedCourseRepository;
    }

    @Transactional(readOnly = true)
    public PlannedCourseListResponseDto getPlannedCourses(Long studentId) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
        return buildResponse(studentId, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto addPlannedCourse(Long studentId, PlannedCourseRequestDto request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        PlannedCourse plannedCourse = PlannedCourse.create(
                student,
                requireYear(request.targetYear()),
                requireSemester(request.targetSemester()),
                requireText(request.courseCode(), "학수번호"),
                requireText(request.courseName(), "교과목명"),
                normalizeText(request.category()),
                requireCredit(request.credit())
        );
        plannedCourseRepository.save(plannedCourse);

        return buildResponse(studentId, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
    }

    @Transactional
    public PlannedCourseListResponseDto deletePlannedCourse(Long studentId, Long plannedCourseId) {
        if (!studentRepository.existsById(studentId)) {
            throw new IllegalArgumentException("학생을 찾을 수 없습니다.");
        }

        plannedCourseRepository.deleteByIdAndStudentId(plannedCourseId, studentId);
        return buildResponse(studentId, plannedCourseRepository.findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(studentId));
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
                        "PLANNED",
                        "",
                        "0"
                ))
                .toList();
    }

    private PlannedCourseListResponseDto buildResponse(Long studentId, List<PlannedCourse> courses) {
        Map<String, SemesterAccumulator> semesterMap = new LinkedHashMap<>();
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (PlannedCourse course : courses) {
            String key = course.getTargetYear() + "-" + course.getTargetSemester();
            SemesterAccumulator accumulator = semesterMap.computeIfAbsent(
                    key,
                    ignored -> new SemesterAccumulator(course.getTargetYear(), course.getTargetSemester())
            );
            accumulator.courses.add(PlannedCourseResponseDto.from(course));

            BigDecimal credit = toDecimal(course.getCredit());
            accumulator.totalCredits = accumulator.totalCredits.add(credit);
            totalCredits = totalCredits.add(credit);
        }

        List<PlannedSemesterSummaryDto> semesters = semesterMap.values().stream()
                .map(accumulator -> new PlannedSemesterSummaryDto(
                        accumulator.targetYear,
                        accumulator.targetSemester,
                        formatDecimal(accumulator.totalCredits),
                        List.copyOf(accumulator.courses)
                ))
                .toList();

        return new PlannedCourseListResponseDto(studentId, formatDecimal(totalCredits), semesters);
    }

    private Integer requireYear(Integer targetYear) {
        if (targetYear == null || targetYear < 2000 || targetYear > 2100) {
            throw new IllegalArgumentException("계획 수강 연도를 확인해주세요.");
        }
        return targetYear;
    }

    private Integer requireSemester(Integer targetSemester) {
        if (targetSemester == null || (targetSemester != 1 && targetSemester != 2)) {
            throw new IllegalArgumentException("계획 수강 학기는 1 또는 2만 가능합니다.");
        }
        return targetSemester;
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

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private BigDecimal toDecimal(String value) {
        return new BigDecimal(value.trim());
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static final class SemesterAccumulator {
        private final Integer targetYear;
        private final Integer targetSemester;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private final List<PlannedCourseResponseDto> courses = new ArrayList<>();

        private SemesterAccumulator(Integer targetYear, Integer targetSemester) {
            this.targetYear = targetYear;
            this.targetSemester = targetSemester;
        }
    }
}
