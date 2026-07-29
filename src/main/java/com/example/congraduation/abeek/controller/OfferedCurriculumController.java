package com.example.congraduation.abeek.controller;

import com.example.congraduation.abeek.dto.OfferedCurriculumResponse;
import com.example.congraduation.abeek.service.OfferedCurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/abeek")
@RequiredArgsConstructor
@Tag(name = "ABEEK Offered Courses", description = "이수체계도 ∩ 강의시간표 개설과목 조회 (공통교양 제외)")
public class OfferedCurriculumController {

    private final OfferedCurriculumService offeredCurriculumService;

    @GetMapping("/timetable-terms")
    @Operation(summary = "적재된 강의시간표 학기 목록")
    public List<Map<String, Object>> timetableTerms() {
        return offeredCurriculumService.availableTerms();
    }

    @GetMapping("/offered-courses")
    @Operation(
            summary = "학과 이수체계도 기준 이번 학기 개설과목",
            description = "사용자 학과의 이수체계도(BSM/전공) 중 해당 학기 강의시간표에 실제로 개설된 과목만 반환합니다. "
                    + "공통교양/균형교양/교양선택은 제외합니다."
    )
    public OfferedCurriculumResponse offeredCourses(
            @Parameter(description = "ABEEK 학과코드", example = "CSE")
            @RequestParam String departmentCode,
            @Parameter(description = "이수체계도 기준 연도(보통 입학연도)", example = "2021")
            @RequestParam int curriculumYear,
            @Parameter(description = "시간표 연도 (미입력 시 최신)", example = "2026")
            @RequestParam(required = false) Integer termYear,
            @Parameter(description = "시간표 학기 1 또는 2 (미입력 시 최신)", example = "1")
            @RequestParam(required = false) Integer semester
    ) {
        return offeredCurriculumService.listOfferedCourses(departmentCode, curriculumYear, termYear, semester);
    }

    @GetMapping("/offered-courses/by-student")
    @Operation(
            summary = "ABEEK 학생 기준 개설과목 조회",
            description = "이미 등록된 ABEEK 학생의 departmentCode/entranceYear로 개설과목을 조회합니다."
    )
    public OfferedCurriculumResponse offeredCoursesByStudent(
            @RequestParam String studentId,
            @RequestParam(required = false) Integer termYear,
            @RequestParam(required = false) Integer semester
    ) {
        return offeredCurriculumService.listOfferedCoursesForStudent(studentId, termYear, semester);
    }
}
