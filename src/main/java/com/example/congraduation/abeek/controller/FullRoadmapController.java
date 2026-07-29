package com.example.congraduation.abeek.controller;

import com.example.congraduation.abeek.dto.FullRoadmapResponse;
import com.example.congraduation.abeek.service.FullRoadmapService;
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
@RequestMapping("/api/abeek")
@RequiredArgsConstructor
@Tag(
        name = "ABEEK Full Roadmap",
        description = "레거시: ABEEK 이수체계도 시드 기반 로드맵. "
                + "전학생 공통 로드맵은 GET /api/roadmap/by-student 를 사용하세요 (강의시간표 + 기이수 학수번호)."
)
public class FullRoadmapController {

    private final FullRoadmapService fullRoadmapService;
    private final StudentRoadmapService studentRoadmapService;

    @GetMapping("/full-roadmap")
    @Operation(
            summary = "[레거시] ABEEK 이수체계도 로드맵",
            description = "ABEEK 커리큘럼 시드(recommendedTerm) 기반입니다. "
                    + "일반 학생 로드맵은 /api/roadmap 를 사용하세요."
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
            summary = "학생 로드맵 (시간표 + 기이수) — /api/roadmap/by-student 와 동일",
            description = "studentDbId(앱 Student PK)로 강의시간표 기반 1~8학기 로드맵을 반환합니다. "
                    + "이수 여부는 기이수성적 학수번호 매칭. 공학인증 대상이면 categories 분할."
    )
    public StudentRoadmapResponse fullRoadmapByStudent(
            @Parameter(description = "앱 Student DB PK (Long)", example = "1", required = true)
            @RequestParam Long studentDbId
    ) {
        return studentRoadmapService.getByStudent(studentDbId);
    }
}
