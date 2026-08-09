package com.example.congraduation.controller;

import com.example.congraduation.auth.AuthenticatedStudent;
import com.example.congraduation.auth.AuthenticatedStudentResolver;
import com.example.congraduation.dto.feedback.CreateFeedbackRequestDto;
import com.example.congraduation.dto.feedback.FeedbackResponseDto;
import com.example.congraduation.service.feedback.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedbacks", description = "오류 신고 / 문의사항 API")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AuthenticatedStudentResolver authenticatedStudentResolver;

    @PostMapping
    @Operation(summary = "오류 신고·문의 접수", description = "로그인한 학생 기준으로 문의를 저장합니다.")
    public FeedbackResponseDto create(
            @RequestBody CreateFeedbackRequestDto request,
            HttpServletRequest httpServletRequest
    ) {
        AuthenticatedStudent authenticatedStudent = authenticatedStudentResolver.require(httpServletRequest);
        return feedbackService.create(request, authenticatedStudent.studentId());
    }

    @GetMapping("/mine")
    @Operation(summary = "내 문의·오류 신고 목록")
    public List<FeedbackResponseDto> listMine(HttpServletRequest httpServletRequest) {
        AuthenticatedStudent authenticatedStudent = authenticatedStudentResolver.require(httpServletRequest);
        return feedbackService.listMine(authenticatedStudent.studentId());
    }
}
