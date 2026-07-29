package com.example.congraduation.controller;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.MajorCreditSummaryDto;
import com.example.congraduation.dto.transcript.TranscriptStatusResponseDto;
import com.example.congraduation.dto.transcript.TranscriptUploadResponseDto;
import com.example.congraduation.service.transcript.MajorCreditSummaryService;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import com.example.congraduation.service.transcript.UnifiedTranscriptUploadService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transcripts")
@Tag(
        name = "Transcript",
        description = "기이수성적 단일 업로드 API. 졸업요건과 공학인증에 같은 저장본을 사용합니다."
)
public class TranscriptController {

    private final TranscriptStorageService transcriptStorageService;
    private final MajorCreditSummaryService majorCreditSummaryService;
    private final UnifiedTranscriptUploadService unifiedTranscriptUploadService;

    public TranscriptController(
            TranscriptStorageService transcriptStorageService,
            MajorCreditSummaryService majorCreditSummaryService,
            UnifiedTranscriptUploadService unifiedTranscriptUploadService
    ) {
        this.transcriptStorageService = transcriptStorageService;
        this.majorCreditSummaryService = majorCreditSummaryService;
        this.unifiedTranscriptUploadService = unifiedTranscriptUploadService;
    }

    @GetMapping("/status/{studentDbId}")
    @Operation(
            summary = "성적표 업로드 상태 조회",
            description = "path의 studentDbId는 앱 Student 테이블 PK(Long)입니다. 학번(studentNo)이 아닙니다."
    )
    public ResponseEntity<TranscriptStatusResponseDto> getStatus(
            @Parameter(description = "앱 Student DB PK (Long). 학번 아님", example = "1")
            @PathVariable Long studentDbId
    ) {
        return ResponseEntity.ok(new TranscriptStatusResponseDto(
                studentDbId,
                transcriptStorageService.hasTranscript(studentDbId)
        ));
    }

    @PostMapping(value = "/upload/{studentDbId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "기이수성적 단일 업로드 (졸업요건 + 공학인증)",
            description = "엑셀을 한 번만 올리면 됩니다. "
                    + "① 성적 DB 저장 ② 졸업 진행도 ③ 전필/전선 합계 ④ ABEEK 학생/enrollment 동기화 및 판정을 한 요청에서 처리합니다. "
                    + "이후 조회만 하면 됩니다: GET /api/evaluate/graduation-progress/{studentDbId}, "
                    + "GET /api/abeek/students/{학번}/abeek-evaluation. "
                    + "파일을 ABEEK용으로 따로 다시 올릴 필요 없습니다."
    )
    public ResponseEntity<TranscriptUploadResponseDto> upload(
            @Parameter(description = "앱 Student DB PK (Long). 학번 아님", example = "1")
            @PathVariable Long studentDbId,
            @Parameter(description = "세종대학교 기이수성적 엑셀 파일(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(unifiedTranscriptUploadService.uploadOnce(studentDbId, file));
    }

    @GetMapping("/{studentDbId}/major-credits")
    @Operation(
            summary = "전필/전선 이수 학점 합계",
            description = "이미 업로드된 기이수성적에서 전필·전선 학점을 합산합니다. path는 Student DB PK(Long)입니다."
    )
    public ResponseEntity<MajorCreditSummaryDto> majorCredits(
            @Parameter(description = "앱 Student DB PK (Long). 학번 아님", example = "1")
            @PathVariable Long studentDbId
    ) {
        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.getLatestTranscriptRows(studentDbId);
        return ResponseEntity.ok(majorCreditSummaryService.summarize(courses));
    }
}
