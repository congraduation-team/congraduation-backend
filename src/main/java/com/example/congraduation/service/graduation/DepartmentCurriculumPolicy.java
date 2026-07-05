package com.example.congraduation.service.graduation;

import java.util.Map;

public record DepartmentCurriculumPolicy(
        String departmentKey,
        int admissionYear,
        int commonLiberalCredits,
        int balancedLiberalCredits,
        int academicFoundationCredits,
        Integer majorFoundationCredits,
        int graduationCredits,
        int majorTotalCredits,
        int majorRequiredCredits,
        int majorElectiveCredits,
        Map<String, Integer> categoryRequiredCredits
) {
}
