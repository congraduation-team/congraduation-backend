package com.example.congraduation.dto.graduation;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BalancedLiberalAreaProgressDto(
        @Schema(description = "균형교양 영역", example = "역사와사상")
        String area,
        @Schema(description = "해당 영역 이수 학점", example = "3")
        String earnedCredits,
        @Schema(description = "해당 영역 충족 여부", example = "true")
        boolean satisfied,
        @Schema(description = "해당 영역으로 인정된 과목 목록")
        List<CategoryCourseDto> courses
) {
}
