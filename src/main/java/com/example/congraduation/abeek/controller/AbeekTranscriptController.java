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
@Tag(name = "ABEEK Transcript", description = "기이수성적 기반 공학인증 판정 API")
public class AbeekTranscriptController {

    private final AbeekTranscriptEvaluationService transcriptEvaluationService;

    @PostMapping(value = "/evaluate-from-transcript", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "기이수성적으로 ABEEK 판정",
            description = "세종 기이수성적 엑셀을 업로드하면 개설학과코드로 학과를 추론하고, "
                    + "과목명을 ABEEK 커리큘럼에 매칭한 뒤 공학인증 요건을 판정합니다. "
                    + "studentId 파라미터는 학번(studentNo)입니다. DB PK가 아닙니다. "
                    + "실패 시 BAD_REQUEST + message에 파싱/학과/매칭 원인을 담습니다."
    )
    public AbeekTranscriptEvaluationResponse evaluateFromTranscript(
            @Parameter(description = "세종대학교 기이수성적 엑셀(.xlsx)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "학번(studentNo). DB PK 아님. 미입력 시 임시 ID 생성", example = "21012345")
            @RequestParam(required = false) String studentId,
            @Parameter(description = "이름")
            @RequestParam(required = false) String name,
            @Parameter(description = "입학 연도 강제 지정 (미입력 시 학번/최소 수강연도 추론)")
            @RequestParam(required = false) Integer entranceYear,
            @Parameter(description = "졸업 ABEEK 연도 강제 지정 (미입력 시 기이수 마지막 수강 학기의 연도. 1학기/2학기 모두 해당 연도)")
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

    @PostMapping("/evaluate-from-stored-transcript")
    @Operation(
            summary = "저장된 기이수성적으로 ABEEK 판정",
            description = "이미 POST /api/transcripts/upload/{studentDbId} 로 저장된 성적으로 파일을 다시 올리지 않고 "
                    + "ABEEK 학생/enrollment를 갱신한 뒤 공학인증을 판정합니다. "
                    + "졸업 ABEEK 연도는 기이수 마지막 수강 학기(1학기 또는 2학기)의 연도를 사용합니다. "
                    + "studentDbId(DB PK) 또는 studentNo(학번) 중 하나를 반드시 전달하세요."
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
