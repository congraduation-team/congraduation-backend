package com.example.congraduation.dto.graduation;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.dto.graduation.RequirementCourseDto;
import com.example.congraduation.dto.plan.PlannedSemesterSummaryDto;
import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CategorySummaryDto;
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
        @Schema(description = "계획 과목까지 반영한 졸업 가능 여부", example = "false")
        boolean graduationEligible,
        @Schema(description = "졸업이 아직 불가능한 경우 남은 조건 목록")
        List<String> graduationBlockers,
        @Schema(description = "추가 전공 트랙 진행도")
        List<MajorTrackProgressDto> majorTracks,
        @Schema(description = "단일전공 졸업작품 진행도")
        GraduationWorkProgressDto graduationWork,
        @Schema(description = "영어졸업인증 진행도")
        EnglishCertificationProgressDto englishCertification,
        @Schema(description = "SW코딩졸업인증 진행도")
        SwCodingCertificationProgressDto swCodingCertification,
        @Schema(description = "총 이수학점 진행도")
        CreditProgressDto totalCredits,
        @Schema(description = "공통교양 진행도")
        CategoryProgressDto commonLiberalCredits,
        @Schema(description = "공통교양(공필/교필) 상세 과목 목록")
        List<CategoryCourseDto> commonLiberalCourses,
        @Schema(description = "주전공 필수 미이수 과목 목록")
        List<RequirementCourseDto> remainingMajorRequiredCourses,
        @Schema(description = "교양선택 진행도")
        CategoryProgressDto electiveLiberalCredits,
        @Schema(description = "균형교양 진행도")
        CategoryProgressDto balancedLiberalCredits,
        @Schema(description = "균형교양 미충족 영역 목록")
        List<String> missingBalancedLiberalAreas,
        @Schema(description = "균형교양 필요 영역 수", example = "3")
        Integer balancedLiberalRequiredAreaCount,
        @Schema(description = "균형교양 충족 영역 수", example = "2")
        Integer balancedLiberalCompletedAreaCount,
        @Schema(description = "균형교양 영역별 진행도")
        List<BalancedLiberalAreaProgressDto> balancedLiberalAreaProgresses,
        @Schema(description = "기초필수(학문기초) 진행도")
        CategoryProgressDto academicFoundationCredits,
        @Schema(description = "전공기초 진행도")
        CategoryProgressDto majorFoundationCredits,
        @Schema(description = "평균 평점", example = "3.24")
        String averageGradePoint,
        @Schema(description = "전공 평점", example = "3.10")
        String majorGradePoint,
        @Schema(description = "교양 평점", example = "3.13")
        String liberalGradePoint,
        @Schema(description = "계획 과목 총 학점", example = "36")
        String plannedCredits,
        @Schema(description = "남은 학기 계획 과목")
        List<PlannedSemesterSummaryDto> plannedSemesters,
        @Schema(description = "전공학점 요약")
        MajorCreditSummaryDto majorCredits,
        @Schema(description = "이수구분별 진행도")
        List<CategorySummaryDto> categorySummaries
) {
}
