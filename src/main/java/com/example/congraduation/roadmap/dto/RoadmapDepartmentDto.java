package com.example.congraduation.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoadmapDepartmentDto {

    @Schema(
            description = "시간표 개설학과명 (한글). GET /api/roadmap?departmentName= 에 전달",
            example = "창의소프트학부 디자인이노베이션전공"
    )
    private final String departmentName;

    @Schema(
            description = "학생 전공명 등으로도 조회 가능한 별칭. 예: 디자인이노베이션전공",
            example = "[\"디자인이노베이션전공\"]"
    )
    private final List<String> aliases;

    @Schema(description = "단과대학", example = "소프트웨어융합대학")
    private final String college;

    @Schema(description = "공학인증 대상 학과 여부")
    private final boolean abeekTarget;

    @Schema(description = "공학인증 학과코드 (대상일 때만)", example = "CSE")
    private final String abeekDepartmentCode;
}
