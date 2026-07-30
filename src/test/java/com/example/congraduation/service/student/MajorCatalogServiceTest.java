package com.example.congraduation.service.student;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MajorCatalogServiceTest {

    private final MajorCatalogService service = new MajorCatalogService();

    @Test
    void excludesDisallowedDoubleMajorDepartmentsFromOptions() {
        assertFalse(service.getMajorOptions().stream()
                .anyMatch(option -> "국방AI융합시스템공학과".equals(option.name())));
        assertFalse(service.getMajorOptions().stream()
                .anyMatch(option -> "항공시스템공학전공".equals(option.name())));
    }

    @Test
    void keepsAllowedDepartmentInOptions() {
        assertTrue(service.getMajorOptions().stream()
                .anyMatch(option -> "컴퓨터공학과".equals(option.name())));
    }
}
