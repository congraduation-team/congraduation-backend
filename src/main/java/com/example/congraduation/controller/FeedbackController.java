package com.example.congraduation.controller;

import com.example.congraduation.dto.feedback.CreateFeedbackRequestDto;
import com.example.congraduation.dto.feedback.FeedbackResponseDto;
import com.example.congraduation.service.feedback.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @PostMapping
    @Operation(summary = "오류 신고·문의 접수", description = "type=BUG|INQUIRY. studentId가 있으면 학생 정보로 스냅샷을 채웁니다.")
    public FeedbackResponseDto create(@RequestBody CreateFeedbackRequestDto request) {
        return feedbackService.create(request);
    }

    @GetMapping("/mine")
    @Operation(summary = "내 문의·오류 신고 목록")
    public List<FeedbackResponseDto> listMine(
            @Parameter(description = "학생 DB PK", required = true, example = "1")
            @RequestParam Long studentId
    ) {
        return feedbackService.listMine(studentId);
    }
}
