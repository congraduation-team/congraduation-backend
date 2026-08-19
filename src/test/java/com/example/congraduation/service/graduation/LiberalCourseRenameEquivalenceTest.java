package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiberalCourseRenameEquivalenceTest {

    private final BalancedLiberalCoursePolicyService policy = new BalancedLiberalCoursePolicyService();

    @Test
    void seminarAndCareerDesignAreTheSameCourse() {
        assertTrue(LiberalCourseRenameEquivalence.sameFamily("신입생세미나", "세종인을위한진로설계"));
        assertTrue(LiberalCourseRenameEquivalence.sameFamily("신입생세미나A", "세종인을위한진로설계"));
        assertFalse(LiberalCourseRenameEquivalence.sameFamily("신입생세미나", "세종인을위한전공탐색"));
        assertFalse(LiberalCourseRenameEquivalence.sameFamily("신입생세미나", "대학생활과진로탐색"));
    }

    @Test
    void campusLifeAndMajorExplorationAreTheSameCourse() {
        assertTrue(LiberalCourseRenameEquivalence.sameFamily("대학생활과진로탐색", "세종인을위한전공탐색"));
        assertTrue(LiberalCourseRenameEquivalence.sameFamily("신입생세미나B", "세종인을위한전공탐색"));
        assertFalse(LiberalCourseRenameEquivalence.sameFamily("대학생활과진로탐색", "세종인을위한진로설계"));
    }

    @Test
    void completedSeminarSatisfies2024CareerDesignNotMajorExploration() {
        CategoryCourseDto careerDesign = new CategoryCourseDto("GEN_CAREER_DESIGN", "세종인을위한진로설계", "1");
        CategoryCourseDto majorExploration = new CategoryCourseDto("GEN_MAJOR_EXPLORATION", "세종인을위한전공탐색", "1");
        List<CompletedCourseUploadRowDto> seminar = List.of(row("신입생세미나"));

        assertTrue(policy.hasCompletedEquivalent(careerDesign, seminar));
        assertFalse(policy.hasCompletedEquivalent(majorExploration, seminar));
    }

    @Test
    void completedCampusLifeSatisfies2021LegacyAnd2024MajorExploration() {
        CategoryCourseDto legacy = new CategoryCourseDto("GEN_CAREER_LEGACY", "대학생활과진로탐색", "1");
        CategoryCourseDto majorExploration = new CategoryCourseDto("GEN_MAJOR_EXPLORATION", "세종인을위한전공탐색", "1");
        CategoryCourseDto careerDesign = new CategoryCourseDto("GEN_CAREER_DESIGN", "세종인을위한진로설계", "1");
        List<CompletedCourseUploadRowDto> campusLife = List.of(row("대학생활과진로탐색"));

        assertTrue(policy.hasCompletedEquivalent(legacy, campusLife));
        assertTrue(policy.hasCompletedEquivalent(majorExploration, campusLife));
        assertFalse(policy.hasCompletedEquivalent(careerDesign, campusLife));
    }

    @Test
    void completedSeminarDoesNotSatisfy2021CampusLifeRequirement() {
        CategoryCourseDto legacy = new CategoryCourseDto("GEN_CAREER_LEGACY", "대학생활과진로탐색", "1");
        assertFalse(policy.hasCompletedEquivalent(legacy, List.of(row("신입생세미나"))));
        assertTrue(policy.hasCompletedEquivalent(legacy, List.of(row("세종인을위한전공탐색"))));
    }

    private static CompletedCourseUploadRowDto row(String courseName) {
        return new CompletedCourseUploadRowDto(
                "2021", "1학기", "GEN000", courseName, "교필", "1", "GRADE", "A0", "4.0");
    }
}
