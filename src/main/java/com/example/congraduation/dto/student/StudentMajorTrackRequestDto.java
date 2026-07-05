package com.example.congraduation.dto.student;

import com.example.congraduation.domain.MajorType;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentMajorTrackRequestDto(
        @Schema(description = "전공 트랙 유형", example = "DOUBLE_MAJOR")
        MajorType trackType,
        @Schema(description = "학과명", example = "경제학과")
        String departmentCode,
        @Schema(description = "승인 학기", example = "4")
        Integer approvedAtSemester,
        @Schema(description = "교직 복수전공 여부", example = "false")
        Boolean teachingCert
) {
}
