package com.example.congraduation.controller;

import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse;
import com.example.congraduation.abeek.service.AbeekTranscriptEvaluationService;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.MajorCreditSummaryDto;
import com.example.congraduation.dto.transcript.TranscriptStatusResponseDto;
import com.example.congraduation.dto.transcript.TranscriptUploadResponseDto;
import com.example.congraduation.service.transcript.MajorCreditSummaryService;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import com.example.congraduation.service.transcript.TranscriptSummaryCalculator;
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
    private final MajorCreditSummaryService majorCreditSummaryService;
    private final AbeekTranscriptEvaluationService abeekTranscriptEvaluationService;

    public TranscriptController(
            TranscriptStorageService transcriptStorageService,
            TranscriptSummaryCalculator transcriptSummaryCalculator,
            MajorCreditSummaryService majorCreditSummaryService,
            AbeekTranscriptEvaluationService abeekTranscriptEvaluationService
    ) {
        this.transcriptStorageService = transcriptStorageService;
        this.transcriptSummaryCalculator = transcriptSummaryCalculator;
        this.majorCreditSummaryService = majorCreditSummaryService;
        this.abeekTranscriptEvaluationService = abeekTranscriptEvaluationService;
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
            summary = "성적 엑셀 업로드 (+ ABEEK 동기화)",
            description = "기이수성적 엑셀을 저장한 뒤, 같은 데이터로 ABEEK 학생/enrollment를 학번(studentNo) 기준으로 upsert하고 공학인증 판정도 수행합니다. "
                    + "path의 studentDbId는 앱 Student DB PK(Long)입니다. "
                    + "ABEEK 실패 시에도 성적 저장은 유지되며 abeekError에 원인이 담깁니다. "
                    + "이후 공학인증 조회는 GET /api/abeek/students/{학번}/abeek-evaluation 을 사용하세요."
    )
    public ResponseEntity<TranscriptUploadResponseDto> upload(
            @Parameter(description = "앱 Student DB PK (Long). 학번 아님", example = "1")
            @PathVariable Long studentDbId,
            @Parameter(description = "세종대학교 기이수성적 엑셀 파일(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        Student student = transcriptStorageService.getStudentOrThrow(studentDbId);
        List<CompletedCourseUploadRowDto> courses;
        try {
            courses = transcriptStorageService.replaceTranscript(studentDbId, file);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("[기이수 저장] " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "[기이수 저장] 성적 저장 중 오류: " + rootMessage(ex),
                    ex
            );
        }

        AbeekTranscriptEvaluationResponse abeek = null;
        String abeekError = null;
        try {
            abeek = abeekTranscriptEvaluationService.evaluateFromRows(
                    courses,
                    student.getStudentNo(),
                    student.getName(),
                    student.getAdmissionYear(),
                    null,
                    null,
                    student.getMajor()
            );
        } catch (Exception ex) {
            // 공학인증 실패해도 기이수 저장은 유지. 프론트는 abeekError로 단계 구분.
            abeekError = "[공학인증 연동] " + rootMessage(ex);
        }

        return ResponseEntity.ok(
                TranscriptUploadResponseDto.from(
                        student.getId(),
                        student.getStudentNo(),
                        courses,
                        transcriptSummaryCalculator.summarize(courses),
                        abeek,
                        abeekError
                )
        );
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String message = cur.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message;
    }

    @GetMapping("/{studentDbId}/major-credits")
    @Operation(
            summary = "전필/전선 이수 학점 합계",
            description = "업로드된 기이수성적에서 전필·전선 학점을 합산합니다. path는 Student DB PK(Long)입니다."
    )
    public ResponseEntity<MajorCreditSummaryDto> majorCredits(
            @Parameter(description = "앱 Student DB PK (Long). 학번 아님", example = "1")
            @PathVariable Long studentDbId
    ) {
        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.getLatestTranscriptRows(studentDbId);
        return ResponseEntity.ok(majorCreditSummaryService.summarize(courses));
    }

    @PostMapping(value = "/major-credits/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "엑셀 직접 업로드로 전필/전선 학점 합계",
            description = "DB 저장 없이 기이수성적 엑셀만으로 전필/전선 학점 합계를 계산합니다."
    )
    public ResponseEntity<MajorCreditSummaryDto> majorCreditsFromFile(
            @Parameter(description = "세종대학교 기이수성적 엑셀 파일(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.parseOnly(file);
        return ResponseEntity.ok(majorCreditSummaryService.summarize(courses));
    }
}
