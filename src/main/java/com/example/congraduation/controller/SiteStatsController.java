package com.example.congraduation.controller;

import com.example.congraduation.dto.stats.RecordVisitRequestDto;
import com.example.congraduation.dto.stats.RecordVisitResponseDto;
import com.example.congraduation.dto.stats.SiteStatsResponseDto;
import com.example.congraduation.service.stats.SiteStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Site Stats", description = "사이트 방문·실사용 통계 API")
public class SiteStatsController {

    private final SiteStatsService siteStatsService;

    public SiteStatsController(SiteStatsService siteStatsService) {
        this.siteStatsService = siteStatsService;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "방문·실사용 통계 조회",
            description = "당일/월/누적 로그인 순 방문자 수와 기이수 업로드 실사용자 수를 반환합니다. (Asia/Seoul)"
    )
    public SiteStatsResponseDto summary() {
        return siteStatsService.getSummary();
    }

    @PostMapping("/visit")
    @Operation(
            summary = "방문 기록",
            description = """
                    로그인 사용자 방문 기록용입니다. studentId가 있을 때만 집계합니다.
                    student:{id}로 저장되며, 로그인 API에서도 자동 기록됩니다.
                    같은 학생은 하루 1회만 순 방문으로 집계됩니다.
                    """
    )
    public RecordVisitResponseDto recordVisit(@RequestBody(required = false) RecordVisitRequestDto request) {
        if (request == null || request.studentId() == null) {
            return new RecordVisitResponseDto("", false);
        }
        return siteStatsService.recordVisit(request.visitorKey(), request.studentId());
    }
}
