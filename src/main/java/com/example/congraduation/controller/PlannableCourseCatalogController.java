package com.example.congraduation.controller;

import com.example.congraduation.dto.plan.PlannableCourseCatalogResponseDto;
import com.example.congraduation.service.plan.PlannableCourseCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planned-courses/catalog")
@Tag(name = "PlannableCourseCatalog", description = "최근 1년 시간표 기반 계획용 교과목 조회 API")
public class PlannableCourseCatalogController {

    private final PlannableCourseCatalogService plannableCourseCatalogService;

    public PlannableCourseCatalogController(PlannableCourseCatalogService plannableCourseCatalogService) {
        this.plannableCourseCatalogService = plannableCourseCatalogService;
    }

    @GetMapping
    @Operation(
            summary = "계획용 교과목 목록 조회",
            description = "시간표 문서에서 중복 교과목명을 제거한 계획용 교과목 목록을 반환합니다. "
                    + "교과목명 검색, 대상학년, 개설학기 필터를 지원합니다."
    )
    public ResponseEntity<PlannableCourseCatalogResponseDto> getCatalog(
            @Parameter(description = "학생 ID. 전달하면 학생 전공/기이수 기준으로 맞춤 검색합니다.", example = "1")
            @RequestParam(required = false) Long studentId,
            @Parameter(description = "교과목명 검색어", example = "자료구조")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "대상학년 필터", example = "2")
            @RequestParam(required = false) String targetGrade,
            @Parameter(description = "검색 대상 학기 번호. 전달하면 최신 해당 학기 시간표를 사용합니다.", example = "1")
            @RequestParam(required = false) Integer semester,
            @Parameter(description = "개설학기 필터", example = "2025-2")
            @RequestParam(required = false) String offeredTerm,
            @Parameter(description = "개설학과 필터", example = "컴퓨터공학과")
            @RequestParam(required = false) String departmentName,
            @Parameter(description = "이수구분 필터", example = "전공필수")
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(
                plannableCourseCatalogService.getCatalog(
                        studentId,
                        keyword,
                        targetGrade,
                        semester,
                        offeredTerm,
                        departmentName,
                        category
                )
        );
    }
}
