package com.example.congraduation.abeek.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.congraduation.abeek.domain.AbeekYearRequirement;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.dto.CurriculumCourseDto;
import com.example.congraduation.abeek.repository.AbeekYearRequirementRepository;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CurriculumQueryService {

    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AbeekYearRequirementRepository requirementRepository;
    private final AdvantageousRequirementService advantageousRequirementService;

    @Transactional(readOnly = true)
    public List<CurriculumCourseDto> getCurriculum(int year) {
        return getCurriculum("CSE", year);
    }

    @Transactional(readOnly = true)
    public List<CurriculumCourseDto> getCurriculum(String departmentCode, int year) {
        return curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear(normalize(departmentCode), year).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AbeekYearRequirement getRequirement(int year) {
        return getRequirement("CSE", year);
    }

    @Transactional(readOnly = true)
    public AbeekYearRequirement getRequirement(String departmentCode, int year) {
        return requirementRepository.findByDepartmentCodeAndYear(normalize(departmentCode), year)
                .orElseThrow(() -> new IllegalArgumentException("ABEEK 요건 없음: " + year));
    }

    @Transactional(readOnly = true)
    public Object getEffectiveRequirement(int entranceYear, int graduationAbeekYear) {
        return getEffectiveRequirement("CSE", entranceYear, graduationAbeekYear);
    }

    @Transactional(readOnly = true)
    public Object getEffectiveRequirement(String departmentCode, int entranceYear, int graduationAbeekYear) {
        return advantageousRequirementService.resolve(normalize(departmentCode), entranceYear, graduationAbeekYear);
    }

    private CurriculumCourseDto toDto(CurriculumCourse c) {
        return CurriculumCourseDto.builder()
                .departmentCode(c.getDepartmentCode())
                .curriculumYear(c.getCurriculumYear())
                .courseCode(c.getCourseMaster().getCourseCode())
                .courseName(c.getCourseMaster().getName())
                .category(c.getCourseMaster().getCategory())
                .role(c.getRole())
                .credits(c.getCredits())
                .designCredits(c.getDesignCredits())
                .designLevel(c.getDesignLevel())
                .electiveArea(c.getCourseMaster().getElectiveArea())
                .recommendedTerm(c.getRecommendedTerm())
                .newlyIntroducedRequired(c.isNewlyIntroducedRequired())
                .build();
    }

    private String normalize(String departmentCode) {
        return departmentCode.trim().toUpperCase(Locale.ROOT);
    }
}
