package com.example.hackathon.dto.student;

import com.example.hackathon.domain.MajorType;
import com.example.hackathon.domain.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record StudentMajorTracksResponseDto(
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(description = "주전공 학과명", example = "컴퓨터공학과")
        String primaryMajor,
        @Schema(description = "기존 호환용 전공 유형", example = "DOUBLE")
        MajorType majorType,
        @Schema(description = "기존 호환용 복수전공 학과명", example = "경제학과")
        String secondaryMajor,
        @Schema(description = "전공 트랙 목록")
        List<StudentMajorTrackResponseDto> tracks
) {
    public static StudentMajorTracksResponseDto from(Student student) {
        return new StudentMajorTracksResponseDto(
                student.getId(),
                student.getMajor(),
                student.getMajorType(),
                student.getSecondaryMajor(),
                student.getMajorTracks().stream()
                        .map(StudentMajorTrackResponseDto::from)
                        .toList()
        );
    }
}
