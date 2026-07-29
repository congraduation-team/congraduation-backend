package com.example.congraduation.abeek.controller;

import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse;
import com.example.congraduation.abeek.service.AbeekTranscriptEvaluationService;
import com.example.congraduation.dto.transcript.TranscriptUploadResponseDto;
import com.example.congraduation.service.transcript.UnifiedTranscriptUploadService;
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
@Tag(
        name = "ABEEK Transcript",
        description = "기이수성적은 /api/transcripts/upload 한 번만 올리면 됩니다. 여기 API는 호환/재판정용입니다."
)
public class AbeekTranscriptController {

    private final UnifiedTranscriptUploadService unifiedTranscriptUploadService;
    private final AbeekTranscriptEvaluationService transcriptEvaluationService;

    @PostMapping(value = "/evaluate-from-transcript", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[통합] 기이수성적 업로드 = 졸업요건 + ABEEK",
            description = "별도 ABEEK 전용 업로드가 아닙니다. "
                    + "POST /api/transcripts/upload/{studentDbId} 와 동일하게 성적을 저장하고 졸업·ABEEK를 함께 처리합니다. "
                    + "프론트는 transcripts/upload 만 호출하면 됩니다. 이 엔드포인트는 하위호환용입니다."
    )
    public TranscriptUploadResponseDto evaluateFromTranscript(
            @Parameter(description = "앱 Student DB PK (Long). 필수. 학번 아님", example = "1", required = true)
            @RequestParam Long studentDbId,
            @Parameter(description = "세종대학교 기이수성적 엑셀(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        return unifiedTranscriptUploadService.uploadOnce(studentDbId, file);
    }

    @PostMapping("/evaluate-from-stored-transcript")
    @Operation(
            summary = "저장된 기이수성적으로 ABEEK만 재판정",
            description = "파일을 다시 올리지 않고, 이미 저장된 성적으로 ABEEK만 다시 돌립니다. "
                    + "최초 업로드는 /api/transcripts/upload 를 사용하세요."
    )
    public AbeekTranscriptEvaluationResponse evaluateFromStoredTranscript(
            @Parameter(description = "앱 Student DB PK (Long). 학번 아님", example = "1")
            @RequestParam(required = false) Long studentDbId,
            @Parameter(description = "학번(studentNo)", example = "21012345")
            @RequestParam(required = false) String studentNo,
            @Parameter(description = "입학 연도 강제 지정")
            @RequestParam(required = false) Integer entranceYear,
            @Parameter(description = "졸업 ABEEK 연도 강제 지정")
            @RequestParam(required = false) Integer graduationAbeekYear,
            @Parameter(description = "ABEEK 학과코드 강제 지정 (예: CSE)")
            @RequestParam(required = false) String departmentCode
    ) {
        return transcriptEvaluationService.evaluateFromStoredTranscript(
                studentDbId,
                studentNo,
                entranceYear,
                graduationAbeekYear,
                departmentCode
        );
    }
}
