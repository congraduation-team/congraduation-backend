package com.example.congraduation.service.transcript;

import com.example.congraduation.abeek.dto.AbeekTranscriptEvaluationResponse;
import com.example.congraduation.abeek.service.AbeekTranscriptEvaluationService;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.GraduationProgressResponseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.dto.transcript.MajorCreditSummaryDto;
import com.example.congraduation.dto.transcript.TranscriptUploadResponseDto;
import com.example.congraduation.service.graduation.GraduationProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 기이수성적 단일 업로드 진입점.
 * 졸업요건(저장·졸업진행)과 공학인증(ABEEK)을 한 번의 파일 업로드로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class UnifiedTranscriptUploadService {

    private final TranscriptStorageService transcriptStorageService;
    private final TranscriptSummaryCalculator transcriptSummaryCalculator;
    private final MajorCreditSummaryService majorCreditSummaryService;
    private final GraduationProgressService graduationProgressService;
    private final AbeekTranscriptEvaluationService abeekTranscriptEvaluationService;

    public TranscriptUploadResponseDto uploadOnce(Long studentDbId, MultipartFile file) {
        Student student = transcriptStorageService.getStudentOrThrow(studentDbId);
        List<CompletedCourseUploadRowDto> courses = transcriptStorageService.replaceTranscript(studentDbId, file);

        MajorCreditSummaryDto majorCredits = majorCreditSummaryService.summarize(courses);

        GraduationProgressResponseDto graduationProgress = null;
        String graduationError = null;
        try {
            graduationProgress = graduationProgressService.evaluate(studentDbId);
        } catch (RuntimeException ex) {
            graduationError = ex.getMessage();
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
        } catch (IllegalArgumentException ex) {
            abeekError = ex.getMessage();
        }

        return TranscriptUploadResponseDto.from(
                student.getId(),
                student.getStudentNo(),
                courses,
                transcriptSummaryCalculator.summarize(courses),
                majorCredits,
                graduationProgress,
                graduationError,
                abeek,
                abeekError
        );
    }
}
