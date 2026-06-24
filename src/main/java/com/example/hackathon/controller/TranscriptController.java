package com.example.hackathon.controller;

import com.example.hackathon.dto.transcript.CompletedCourseUploadRowDto;
import com.example.hackathon.dto.transcript.TranscriptStatusResponseDto;
import com.example.hackathon.dto.transcript.TranscriptUploadResponseDto;
import com.example.hackathon.service.transcript.TranscriptStorageService;
import com.example.hackathon.service.transcript.TranscriptSummaryCalculator;
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
@Tag(name = "Transcript", description = "성적 엑셀 업로드 및 이수 내역 파싱 API")
public class TranscriptController {

    private final TranscriptStorageService transcriptStorageService;
    private final TranscriptSummaryCalculator transcriptSummaryCalculator;

    public TranscriptController(
            TranscriptStorageService transcriptStorageService,
            TranscriptSummaryCalculator transcriptSummaryCalculator
    ) {
        this.transcriptStorageService = transcriptStorageService;
        this.transcriptSummaryCalculator = transcriptSummaryCalculator;
    }

    @GetMapping("/status/{studentId}")
    @Operation(summary = "성적표 업로드 상태 조회", description = "학생의 최신 성적표 업로드 존재 여부를 반환합니다.")
    public ResponseEntity<TranscriptStatusResponseDto> getStatus(@PathVariable Long studentId) {
        return ResponseEntity.ok(new TranscriptStatusResponseDto(
                studentId,
                transcriptStorageService.hasTranscript(studentId)
        ));
    }

    @PostMapping(value = "/upload/{studentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "성적 엑셀 업로드", description = "기이수성적 엑셀 파일을 업로드하면 기존 데이터를 교체하고 DB에 저장한 뒤 요약을 반환합니다.")
    public ResponseEntity<TranscriptUploadResponseDto> upload(
            @PathVariable Long studentId,
            @Parameter(description = "세종대학교 기이수성적 엑셀 파일(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.replaceTranscript(studentId, file);
        return ResponseEntity.ok(
                TranscriptUploadResponseDto.from(
                        courses,
                        transcriptSummaryCalculator.summarize(courses)
                )
        );
    }
}
