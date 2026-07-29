package com.example.congraduation.roadmap.controller;

import com.example.congraduation.roadmap.dto.StudentRoadmapResponse;
import com.example.congraduation.roadmap.service.StudentRoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roadmap")
@RequiredArgsConstructor
@Tag(
        name = "Student Roadmap",
        description = "강의시간표 기반 1~8학기 로드맵. 이수 여부는 기이수성적 학수번호로 표시. "
                + "공학인증 대상 학과면 GENERAL/BSM/MAJOR로 나눠 반환."
)
public class StudentRoadmapController {

    private final StudentRoadmapService studentRoadmapService;

    @GetMapping("/by-student")
    @Operation(
            summary = "학생 로드맵 (시간표 + 기이수)",
            description = "학생 전공의 개설학과 시간표를 gradeYear+학기로 1-1~4-2에 배치하고, "
                    + "업로드된 기이수성적 학수번호로 이수 여부를 표시합니다. "
                    + "전공이 공학인증 대상 학과면 categories(GENERAL/BSM/MAJOR)를 채웁니다."
    )
    public StudentRoadmapResponse byStudent(
            @Parameter(description = "앱 Student DB PK (Long)", example = "1", required = true)
            @RequestParam Long studentDbId
    ) {
        return studentRoadmapService.getByStudent(studentDbId);
    }

    @GetMapping
    @Operation(
            summary = "학과 로드맵 (시간표)",
            description = "학과명 기준으로 시간표 로드맵을 반환합니다. "
                    + "studentDbId를 주면 기이수 학수번호로 이수 표시를 붙입니다."
    )
    public StudentRoadmapResponse byDepartment(
            @Parameter(description = "개설학과명", example = "컴퓨터공학과", required = true)
            @RequestParam String departmentName,
            @Parameter(description = "이수 표시용 Student DB PK (선택)")
            @RequestParam(required = false) Long studentDbId
    ) {
        return studentRoadmapService.getByDepartment(departmentName, studentDbId);
    }
}
