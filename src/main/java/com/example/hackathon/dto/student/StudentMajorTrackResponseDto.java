package com.example.hackathon.dto.student;

import com.example.hackathon.domain.MajorType;
import com.example.hackathon.domain.StudentMajorTrack;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentMajorTrackResponseDto(
        @Schema(description = "트랙 ID", example = "1")
        Long id,
        @Schema(description = "전공 트랙 유형", example = "DOUBLE_MAJOR")
        MajorType trackType,
        @Schema(description = "학과명", example = "경제학과")
        String departmentCode,
        @Schema(description = "승인 학기", example = "4")
        Integer approvedAtSemester,
        @Schema(description = "교직 복수전공 여부", example = "false")
        Boolean teachingCert
) {
    public static StudentMajorTrackResponseDto from(StudentMajorTrack track) {
        return new StudentMajorTrackResponseDto(
                track.getId(),
                track.getTrackType(),
                track.getDepartmentCode(),
                track.getApprovedAtSemester(),
                track.getTeachingCert()
        );
    }
}
