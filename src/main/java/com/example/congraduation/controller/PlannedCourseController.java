package com.example.congraduation.controller;

import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.plan.PlannedCourseRequestDto;
import com.example.congraduation.service.plan.PlannedCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/planned-courses")
@Tag(name = "PlannedCourse", description = "남은 학기 수강 계획 과목 관리 API")
public class PlannedCourseController {

    private final PlannedCourseService plannedCourseService;

    public PlannedCourseController(PlannedCourseService plannedCourseService) {
        this.plannedCourseService = plannedCourseService;
    }

    @GetMapping
    @Operation(summary = "계획 과목 조회", description = "학생의 남은 학기 계획 과목과 학기별 계획 학점을 조회합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> getPlannedCourses(@PathVariable Long studentId) {
        return ResponseEntity.ok(plannedCourseService.getPlannedCourses(studentId));
    }

    @PostMapping
    @Operation(summary = "계획 과목 추가", description = "미래 학기에 들을 계획 과목을 추가하고 계획 학점 요약을 반환합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> addPlannedCourse(
            @PathVariable Long studentId,
            @RequestBody PlannedCourseRequestDto request
    ) {
        return ResponseEntity.ok(plannedCourseService.addPlannedCourse(studentId, request));
    }

    @DeleteMapping("/{plannedCourseId}")
    @Operation(summary = "계획 과목 삭제", description = "등록된 계획 과목을 삭제하고 계획 학점 요약을 반환합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> deletePlannedCourse(
            @PathVariable Long studentId,
            @PathVariable Long plannedCourseId
    ) {
        return ResponseEntity.ok(plannedCourseService.deletePlannedCourse(studentId, plannedCourseId));
    }
}
