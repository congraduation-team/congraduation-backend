package com.example.congraduation.abeek.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.example.congraduation.abeek.domain.AbeekYearRequirement;
import com.example.congraduation.abeek.dto.CurriculumCourseDto;
import com.example.congraduation.abeek.service.CurriculumQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumQueryService curriculumQueryService;

    @GetMapping("/{year}/courses")
    public List<CurriculumCourseDto> courses(@PathVariable int year) {
        return curriculumQueryService.getCurriculum(year);
    }

    @GetMapping("/{departmentCode}/{year}/courses")
    public List<CurriculumCourseDto> courses(@PathVariable String departmentCode, @PathVariable int year) {
        return curriculumQueryService.getCurriculum(departmentCode, year);
    }

    @GetMapping("/{year}/abeek-requirement")
    public AbeekYearRequirement requirement(@PathVariable int year) {
        return curriculumQueryService.getRequirement(year);
    }

    @GetMapping("/{departmentCode}/{year}/abeek-requirement")
    public AbeekYearRequirement requirement(@PathVariable String departmentCode, @PathVariable int year) {
        return curriculumQueryService.getRequirement(departmentCode, year);
    }

    @GetMapping("/effective-requirement")
    public Object effectiveRequirement(
            @RequestParam int entranceYear,
            @RequestParam int graduationAbeekYear,
            @RequestParam(defaultValue = "CSE") String departmentCode
    ) {
        return curriculumQueryService.getEffectiveRequirement(departmentCode, entranceYear, graduationAbeekYear);
    }
}
