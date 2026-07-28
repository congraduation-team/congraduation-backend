package com.example.congraduation.abeek.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class AbeekStudentController {

    private final AbeekStudentService studentService;
    private final AbeekEvaluationService evaluationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateStudentRequest request) {
        AbeekStudent student = studentService.create(request);
        return toSummary(student);
    }

    @GetMapping("/{studentId}")
    public Map<String, Object> get(@PathVariable String studentId) {
        return toSummary(studentService.get(studentId));
    }

    @PostMapping("/{studentId}/enrollments")
    public Map<String, Object> addEnrollment(
            @PathVariable String studentId,
            @Valid @RequestBody AddEnrollmentRequest request
    ) {
        return toSummary(studentService.addEnrollment(studentId, request));
    }

    @GetMapping("/{studentId}/abeek-evaluation")
    public AbeekEvaluationResponse evaluate(@PathVariable String studentId) {
        return evaluationService.evaluate(studentId);
    }

    private Map<String, Object> toSummary(AbeekStudent s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("studentId", s.getStudentId());
        map.put("name", s.getName());
        map.put("entranceYear", s.getEntranceYear());
        map.put("graduationAbeekYear", s.getGraduationAbeekYear());
        map.put("department", s.getDepartment());
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
