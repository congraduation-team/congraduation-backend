package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AbeekTranscriptEvaluationResponse {
    /** ABEEK 학생 식별자 = 학번(studentNo). DB PK가 아님. */
    private final String studentId;
    /** 학번. studentId와 동일 값. */
    private final String studentNo;
    private final String studentName;
    private final String inferredSejongDepartmentCode;
    private final String departmentCode;
    private final String departmentName;
    private final int entranceYear;
    private final int graduationAbeekYear;
    private final int totalCourses;
    private final int matchedCourses;
    private final int unmatchedCourses;
    private final List<MatchedCourseDto> matches;
    private final List<UnmatchedCourseDto> unmatched;
    private final AbeekEvaluationResponse evaluation;

    @Getter
    @Builder
    public static class MatchedCourseDto {
        private final String transcriptCourseCode;
        private final String transcriptCourseName;
        private final String abeekCourseCode;
        private final String abeekCourseName;
        private final int credits;
        private final double designCredits;
        private final int takenYear;
        private final int takenSemester;
        private final boolean passed;
    }

    @Getter
    @Builder
    public static class UnmatchedCourseDto {
        private final String transcriptCourseCode;
        private final String transcriptCourseName;
        private final String category;
        private final String reason;
    }
}
