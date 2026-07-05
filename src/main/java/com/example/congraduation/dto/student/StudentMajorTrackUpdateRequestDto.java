package com.example.congraduation.dto.student;

import com.example.congraduation.domain.MajorType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record StudentMajorTrackUpdateRequestDto(
        @Schema(description = "기존 호환용 전공 유형", example = "DOUBLE")
        MajorType majorType,
        @Schema(description = "기존 호환용 복수전공 학과명. 단일전공이면 null 가능", example = "소프트웨어학과")
        String secondaryMajor,
        @ArraySchema(schema = @Schema(implementation = StudentMajorTrackRequestDto.class))
        List<StudentMajorTrackRequestDto> tracks
) {
}
