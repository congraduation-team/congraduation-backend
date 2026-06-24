package com.example.hackathon.dto.student;

import com.example.hackathon.domain.MajorType;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentMajorTrackUpdateRequestDto(
        @Schema(description = "전공 유형", example = "DOUBLE")
        MajorType majorType,
        @Schema(description = "복수전공 학과명. 단일전공이면 null 가능", example = "소프트웨어학과")
        String secondaryMajor
) {
}
