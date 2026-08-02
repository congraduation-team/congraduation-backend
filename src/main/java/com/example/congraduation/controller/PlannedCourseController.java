package com.example.congraduation.controller;

import com.example.congraduation.dto.plan.PlannedCourseListResponseDto;
import com.example.congraduation.dto.plan.PlannedCourseExpectedGradeRequestDto;
import com.example.congraduation.dto.plan.PlannedCourseRequestDto;
import com.example.congraduation.dto.plan.PlannedSemesterCreateRequestDto;
import com.example.congraduation.service.plan.PlannedCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}")
@Tag(name = "PlannedCourse", description = "남은 학기 수강 계획 과목 관리 API")
public class PlannedCourseController {

    private final PlannedCourseService plannedCourseService;

    public PlannedCourseController(PlannedCourseService plannedCourseService) {
        this.plannedCourseService = plannedCourseService;
    }

    @GetMapping("/planned-courses")
    @Operation(
            summary = "계획 과목 조회",
            description = "학생의 남은 학기 계획 과목과 학기별 계획 학점을 조회합니다. "
                    + "조회 시 마지막 이수 다음~4-2 빈 학기 카드가 없으면 자동 생성합니다."
    )
    public ResponseEntity<PlannedCourseListResponseDto> getPlannedCourses(@PathVariable Long studentId) {
        return ResponseEntity.ok(plannedCourseService.getPlannedCourses(studentId));
    }

    @PostMapping("/planned-semesters/next")
    @Operation(
            summary = "다음 빈 학기 추가",
            description = "count가 1 이하(기본)이면 마지막 이수 다음부터 4-2까지 빈 학기를 모두 확보합니다. "
                    + "count>1이면 그 개수만큼 순차 추가합니다(최대 8학년)."
    )
    public ResponseEntity<PlannedCourseListResponseDto> addNextPlannedSemesters(
            @PathVariable Long studentId,
            @RequestBody(required = false) PlannedSemesterCreateRequestDto request
    ) {
        int count = request == null || request.count() == null ? 1 : request.count();
        return ResponseEntity.ok(plannedCourseService.addNextPlannedSemesters(studentId, count));
    }

    @DeleteMapping("/planned-semesters/{plannedSemesterId}")
    @Operation(summary = "계획 학기 삭제", description = "등록된 계획 학기와 해당 학기에 포함된 계획 과목을 함께 삭제합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> deletePlannedSemester(
            @PathVariable Long studentId,
            @PathVariable Long plannedSemesterId
    ) {
        return ResponseEntity.ok(plannedCourseService.deletePlannedSemester(studentId, plannedSemesterId));
    }

    @PostMapping("/planned-courses")
    @Operation(summary = "계획 과목 추가", description = "미래 학기에 들을 계획 과목을 추가하고 계획 학점 요약을 반환합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> addPlannedCourse(
            @PathVariable Long studentId,
            @RequestBody PlannedCourseRequestDto request
    ) {
        return ResponseEntity.ok(plannedCourseService.addPlannedCourse(studentId, request));
    }

    @PatchMapping("/planned-courses/{plannedCourseId}/expected-grade")
    @Operation(summary = "계획 과목 예상 성적 수정", description = "등록된 계획 과목에 예상 성적을 저장하고 계획 학점 요약을 반환합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> updateExpectedGrade(
            @PathVariable Long studentId,
            @PathVariable Long plannedCourseId,
            @RequestBody PlannedCourseExpectedGradeRequestDto request
    ) {
        return ResponseEntity.ok(plannedCourseService.updateExpectedGrade(studentId, plannedCourseId, request));
    }

    @DeleteMapping("/planned-courses/{plannedCourseId}")
    @Operation(summary = "계획 과목 삭제", description = "등록된 계획 과목을 삭제하고 계획 학점 요약을 반환합니다.")
    public ResponseEntity<PlannedCourseListResponseDto> deletePlannedCourse(
            @PathVariable Long studentId,
            @PathVariable Long plannedCourseId
    ) {
        return ResponseEntity.ok(plannedCourseService.deletePlannedCourse(studentId, plannedCourseId));
    }
}
