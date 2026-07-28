package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RequiredCourseStatusDto {
    private final String courseCode;
    private final String courseName;
    private final boolean completed;
    private final boolean waived;
    private final String note;
}
