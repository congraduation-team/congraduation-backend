package com.example.hackathon.controller;

import com.example.hackathon.dto.graduation.GraduationProgressResponseDto;
import com.example.hackathon.service.graduation.GraduationProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluate")
@Tag(name = "Graduation Progress", description = "학생 성적표 기반 졸업 진행도 분석 API")
public class GraduationProgressController {

    private final GraduationProgressService graduationProgressService;

    public GraduationProgressController(GraduationProgressService graduationProgressService) {
        this.graduationProgressService = graduationProgressService;
    }

    @GetMapping("/graduation-progress/{studentId}")
    @Operation(
            summary = "졸업 진행도 분석",
            description = "DB에 저장된 최신 성적표를 기준으로 총 이수학점, 평균 평점, 이수구분별 진행도를 분석합니다."
    )
    public ResponseEntity<GraduationProgressResponseDto> evaluate(@PathVariable Long studentId) {
        return ResponseEntity.ok(graduationProgressService.evaluate(studentId));
    }
}
