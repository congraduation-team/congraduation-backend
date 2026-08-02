package com.example.congraduation.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlannedCourseListResponseDto(
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(
                description = "기이수 정규학기 순번 기준 마지막 학기(1-1~4-2). "
                        + "takenYear-admissionYear+1 달력 상대학년 아님. FE는 이 값으로 학년 표시.",
                example = "4-1"
        )
        String lastCompletedSemester,
        @Schema(description = "마지막 이수 실제 수강 연도(원본)", example = "2026")
        String lastCompletedTakenYear,
        @Schema(description = "마지막 이수 실제 수강 학기(원본)", example = "1학기")
        String lastCompletedTakenSemester,
        @Schema(description = "기이수 순번 기준 학년. 로드맵 칸은 1~4, 계획 학기는 그 이후도 가능", example = "4")
        Integer standingGradeYear,
        @Schema(description = "기이수 순번이 4학년을 넘는 경우만 true. 달력 연차(2026-2021+1)로 판단하지 말 것")
        boolean overStanding,
        @Schema(description = "전체 계획 학점 합계", example = "42")
        String totalPlannedCredits,
        @Schema(description = "학기별 계획 목록")
        List<PlannedSemesterSummaryDto> semesters
) {
}
