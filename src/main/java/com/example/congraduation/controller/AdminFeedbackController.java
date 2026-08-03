package com.example.congraduation.controller;

import com.example.congraduation.dto.feedback.FeedbackResponseDto;
import com.example.congraduation.dto.feedback.UpdateFeedbackRequestDto;
import com.example.congraduation.service.feedback.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Admin Feedbacks", description = "관리자 오류 신고 / 문의사항 확인 API")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    @Operation(
            summary = "전체 문의·오류 신고 목록",
            description = "최신순. adminStudentId를 넘기면 해당 학생이 관리자인지 검사합니다."
    )
    public List<FeedbackResponseDto> listAll(
            @Parameter(description = "관리자 학생 DB PK (선택, 권한 검증용)", example = "1")
            @RequestParam(required = false) Long adminStudentId
    ) {
        feedbackService.requireAdmin(adminStudentId);
        return feedbackService.listAllForAdmin();
    }

    @PatchMapping("/{id}")
    @Operation(summary = "문의·오류 처리 상태/메모 수정")
    public FeedbackResponseDto update(
            @PathVariable Long id,
            @RequestBody UpdateFeedbackRequestDto request,
            @Parameter(description = "관리자 학생 DB PK (선택, 권한 검증용)", example = "1")
            @RequestParam(required = false) Long adminStudentId
    ) {
        feedbackService.requireAdmin(adminStudentId);
        return feedbackService.updateAsAdmin(id, request);
    }
}
