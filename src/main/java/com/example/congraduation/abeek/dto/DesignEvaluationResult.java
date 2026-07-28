package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DesignEvaluationResult {
    private final double recognizedDesignCredits;
    private final boolean hasBasicDesign;
    private final boolean hasElementDesign;
    private final boolean hasComprehensiveDesign;
    private final boolean sequenceSatisfied;
    private final List<DesignCourseResult> courses;
}
