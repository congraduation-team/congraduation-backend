package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

@Getter
@Builder
public class DesignCourseResult {
    private final String courseCode;
    private final String courseName;
    private final int takenYear;
    private final int takenSemester;
    private final DesignLevel designLevel;
    private final double rawDesignCredits;
    private final double recognizedDesignCredits;
    private final boolean recognized;
    private final String reason;
}
