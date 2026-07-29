package com.example.congraduation.abeek.controller;

import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse;
import com.example.congraduation.abeek.service.AbeekTranscriptEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/abeek")
@RequiredArgsConstructor
@Tag(name = "ABEEK Transcript", description = "기이수성적 엑셀 기반 공학인증 판정 API")
public class AbeekTranscriptController {

    private final AbeekTranscriptEvaluationService transcriptEvaluationService;

    @PostMapping(value = "/evaluate-from-transcript", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "기이수성적으로 ABEEK 판정",
            description = "세종 기이수성적 엑셀을 업로드하면 개설학과코드로 학과를 추론하고, "
                    + "과목명을 ABEEK 커리큘럼에 매칭한 뒤 공학인증 요건을 판정합니다."
    )
    public AbeekTranscriptEvaluationResponse evaluateFromTranscript(
            @Parameter(description = "세종대학교 기이수성적 엑셀(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "학번 (미입력 시 임시 ID 생성)")
            @RequestParam(required = false) String studentId,
            @Parameter(description = "이름")
            @RequestParam(required = false) String name,
            @Parameter(description = "입학 연도 강제 지정 (미입력 시 학번/최소 수강연도 추론)")
            @RequestParam(required = false) Integer entranceYear,
            @Parameter(description = "졸업 ABEEK 연도 강제 지정 (미입력 시 최근 수강연도 사용)")
            @RequestParam(required = false) Integer graduationAbeekYear,
            @Parameter(description = "ABEEK 학과코드 강제 지정 (예: CSE). 미입력 시 개설학과코드로 추론")
            @RequestParam(required = false) String departmentCode
    ) {
        return transcriptEvaluationService.evaluateFromTranscript(
                file,
                studentId,
                name,
                entranceYear,
                graduationAbeekYear,
                departmentCode
        );
    }
}
