package com.example.congraduation.dto.student;

import io.swagger.v3.oas.annotations.media.Schema;

public record MajorOptionDto(
        @Schema(description = "학과명", example = "컴퓨터공학과")
        String name
) {
}
