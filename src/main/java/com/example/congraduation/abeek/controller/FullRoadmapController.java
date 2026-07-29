package com.example.congraduation.abeek.controller;

import com.example.congraduation.abeek.dto.FullRoadmapResponse;
import com.example.congraduation.abeek.service.FullRoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/abeek")
@RequiredArgsConstructor
@Tag(name = "ABEEK Full Roadmap", description = "1~8학기 전체로드맵 (전문교양/BSM/전공, 설계학점, 이수여부)")
public class FullRoadmapController {

    private final FullRoadmapService fullRoadmapService;

    @GetMapping("/full-roadmap")
    @Operation(
            summary = "전체로드맵 조회",
            description = "학과 이수체계도를 1-1 ~ 4-2(8학기) 전체로드맵으로 반환합니다. "
                    + "각 과목에 전문교양/BSM/전공 구분, 설계학점, 공학인증 전공 여부, 이수 여부를 포함합니다."
    )
    public FullRoadmapResponse fullRoadmap(
            @Parameter(description = "ABEEK 학과코드", example = "CSE")
            @RequestParam String departmentCode,
            @Parameter(description = "이수체계도 기준 연도(보통 입학연도)", example = "2021")
            @RequestParam int curriculumYear,
            @Parameter(description = "이수 표시용 ABEEK 학번 (선택)")
            @RequestParam(required = false) String studentId
    ) {
        return fullRoadmapService.getFullRoadmap(departmentCode, curriculumYear, studentId);
    }

    @GetMapping("/full-roadmap/by-student")
    @Operation(
            summary = "학생 기준 전체로드맵",
            description = "ABEEK 학생의 학과/입학연도/이수과목을 사용해 전체로드맵을 반환합니다."
    )
    public FullRoadmapResponse fullRoadmapByStudent(
            @RequestParam String studentId
    ) {
        return fullRoadmapService.getFullRoadmapByStudent(studentId);
    }
}
