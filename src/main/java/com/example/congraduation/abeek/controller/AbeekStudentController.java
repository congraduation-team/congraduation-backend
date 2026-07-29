package com.example.congraduation.abeek.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.example.congraduation.abeek.domain.AbeekStudent;
import com.example.congraduation.abeek.dto.AbeekEvaluationResponse;
import com.example.congraduation.abeek.dto.AddEnrollmentRequest;
import com.example.congraduation.abeek.dto.CreateStudentRequest;
import com.example.congraduation.abeek.service.AbeekEvaluationService;
import com.example.congraduation.abeek.service.AbeekStudentService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/abeek/students")
@RequiredArgsConstructor
@Tag(
        name = "ABEEK Students",
        description = "ABEEK 학생/수강/판정 API. path의 {studentId}는 학번(studentNo)이며 앱 DB PK가 아닙니다."
)
public class AbeekStudentController {

    private final AbeekStudentService studentService;
    private final AbeekEvaluationService evaluationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "ABEEK 학생 생성", description = "request.studentId는 학번(studentNo)입니다.")
    public Map<String, Object> create(@Valid @RequestBody CreateStudentRequest request) {
        AbeekStudent student = studentService.create(request);
        return toSummary(student);
    }

    @GetMapping("/{studentId}")
    @Operation(summary = "ABEEK 학생 조회")
    public Map<String, Object> get(
            @Parameter(description = "학번(studentNo). DB PK 아님", example = "21012345")
            @PathVariable String studentId
    ) {
        return toSummary(studentService.get(studentId));
    }

    @PostMapping("/{studentId}/enrollments")
    @Operation(summary = "ABEEK 수강 과목 추가")
    public Map<String, Object> addEnrollment(
            @Parameter(description = "학번(studentNo). DB PK 아님", example = "21012345")
            @PathVariable String studentId,
            @Valid @RequestBody AddEnrollmentRequest request
    ) {
        return toSummary(studentService.addEnrollment(studentId, request));
    }

    @GetMapping("/{studentId}/abeek-evaluation")
    @Operation(
            summary = "공학인증 판정",
            description = "path의 studentId는 학번(studentNo)입니다. "
                    + "로그인 응답의 studentNo를 사용하세요. Student.id(DB PK)를 넣으면 학생을 찾지 못합니다."
    )
    public AbeekEvaluationResponse evaluate(
            @Parameter(description = "학번(studentNo). DB PK 아님", example = "21012345")
            @PathVariable String studentId
    ) {
        return evaluationService.evaluate(studentId);
    }

    private Map<String, Object> toSummary(AbeekStudent s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("studentId", s.getStudentId());
        map.put("studentNo", s.getStudentId());
        map.put("name", s.getName());
        map.put("entranceYear", s.getEntranceYear());
        map.put("graduationAbeekYear", s.getGraduationAbeekYear());
        map.put("department", s.getDepartment());
        map.put("departmentCode", s.getDepartmentCode());
        map.put("enrollments", s.getEnrollments().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseCode", e.getCourseMaster().getCourseCode());
            m.put("courseName", e.getCourseMaster().getName());
            m.put("credits", e.getCredits());
            m.put("designCredits", e.getDesignCredits());
            m.put("takenYear", e.getTakenYear());
            m.put("takenSemester", e.getTakenSemester());
            m.put("passed", e.isPassed());
            return m;
        }).collect(Collectors.toList()));
        return map;
    }
}
