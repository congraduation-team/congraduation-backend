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
            description = "당일/월/누적 순 방문자 수와 기이수 업로드 실사용자 수를 반환합니다. (Asia/Seoul)"
    )
    public SiteStatsResponseDto summary() {
        return siteStatsService.getSummary();
    }

    @PostMapping("/visit")
    @Operation(
            summary = "방문 기록",
            description = """
                    페이지 진입 시 호출합니다.
                    - 로그인 전: visitorKey(브라우저 고정 UUID) 권장
                    - 로그인 후: studentId 전달 (student:{id}로 집계, 로그인 API에서도 자동 기록)
                    같은 방문자는 하루 1회만 순 방문으로 집계됩니다.
                    """
    )
    public RecordVisitResponseDto recordVisit(@RequestBody(required = false) RecordVisitRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("visitorKey 또는 studentId 중 하나는 필수입니다.");
        }
        return siteStatsService.recordVisit(request.visitorKey(), request.studentId());
    }
}
