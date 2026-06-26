package com.example.hackathon.dto.graduation;

import com.example.hackathon.domain.MajorType;
import com.example.hackathon.dto.transcript.CategorySummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GraduationProgressResponseDto(
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(description = "입학년도", example = "2021")
        Integer admissionYear,
        @Schema(description = "주전공", example = "컴퓨터공학과")
        String major,
        @Schema(description = "전공 유형", example = "SINGLE")
        MajorType majorType,
        @Schema(description = "복수전공 학과", example = "소프트웨어학과")
        String secondaryMajor,
        @Schema(description = "추가 전공 트랙 진행도")
        List<MajorTrackProgressDto> majorTracks,
        @Schema(description = "단일전공 졸업작품 진행도")
        GraduationWorkProgressDto graduationWork,
        @Schema(description = "총 이수학점 진행도")
        CreditProgressDto totalCredits,
        @Schema(description = "공통교양 진행도")
        CategoryProgressDto commonLiberalCredits,
        @Schema(description = "교양선택 진행도")
        CategoryProgressDto electiveLiberalCredits,
        @Schema(description = "균형교양 진행도")
        CategoryProgressDto balancedLiberalCredits,
        @Schema(description = "기초필수(학문기초) 진행도")
        CategoryProgressDto academicFoundationCredits,
        @Schema(description = "전공기초 진행도")
        CategoryProgressDto majorFoundationCredits,
        @Schema(description = "평균 평점", example = "3.24")
        String averageGradePoint,
        @Schema(description = "전공 평점", example = "3.10")
        String majorGradePoint,
        @Schema(description = "전공학점 요약")
        MajorCreditSummaryDto majorCredits,
        @Schema(description = "이수구분별 진행도")
        List<CategorySummaryDto> categorySummaries
) {
}
