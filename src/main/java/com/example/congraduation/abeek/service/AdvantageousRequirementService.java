package com.example.congraduation.abeek.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.congraduation.abeek.domain.AbeekYearRequirement;
import com.example.congraduation.abeek.domain.EffectiveAbeekRequirement;
import com.example.congraduation.abeek.repository.AbeekYearRequirementRepository;

/**
 * 입학 연도 요건과 졸업(막학기) 연도 요건을 비교해
 * 학점 기준은 더 유리한(낮은) 쪽을 적용한다.
 */
@Service
@RequiredArgsConstructor
public class AdvantageousRequirementService {

    private final AbeekYearRequirementRepository requirementRepository;

    @Transactional(readOnly = true)
    public EffectiveAbeekRequirement resolve(int entranceYear, int graduationAbeekYear) {
        return resolve("CSE", entranceYear, graduationAbeekYear);
    }

    @Transactional(readOnly = true)
    public EffectiveAbeekRequirement resolve(String departmentCode, int entranceYear, int graduationAbeekYear) {
        AbeekYearRequirement entrance = requirementRepository.findByDepartmentCodeAndYear(departmentCode, entranceYear)
                .orElseThrow(() -> new IllegalArgumentException("입학 연도 ABEEK 요건 없음: " + departmentCode + " " + entranceYear));
        AbeekYearRequirement graduation = requirementRepository.findByDepartmentCodeAndYear(departmentCode, graduationAbeekYear)
                .orElseThrow(() -> new IllegalArgumentException("졸업 ABEEK 연도 요건 없음: " + departmentCode + " " + graduationAbeekYear));

        IntPick general = pickMin(entrance.getGeneralMinCredits(), entranceYear,
                graduation.getGeneralMinCredits(), graduationAbeekYear);
        IntPick bsm = pickMin(entrance.getBsmMinCredits(), entranceYear,
                graduation.getBsmMinCredits(), graduationAbeekYear);
        IntPick major = pickMin(entrance.getMajorMinCredits(), entranceYear,
                graduation.getMajorMinCredits(), graduationAbeekYear);
        DoublePick design = pickMin(entrance.getDesignMinCredits(), entranceYear,
                graduation.getDesignMinCredits(), graduationAbeekYear);

        // 인증선택은 입학 연도 규칙을 기본으로 하되, 졸업 연도가 더 느슨하면 졸업 연도 적용
        AbeekYearRequirement certSource = moreLenientCert(entrance, graduation);

        return EffectiveAbeekRequirement.builder()
                .departmentCode(departmentCode)
                .entranceYear(entranceYear)
                .graduationAbeekYear(graduationAbeekYear)
                .generalMinCredits(general.value)
                .bsmMinCredits(bsm.value)
                .majorMinCredits(major.value)
                .designMinCredits(design.value)
                .certElectiveMinCourses(certSource.getCertElectiveMinCourses())
                .certElectiveMinCredits(certSource.getCertElectiveMinCredits())
                .certElectiveMinAreas(certSource.getCertElectiveMinAreas())
                .generalSource(general.source)
                .bsmSource(bsm.source)
                .majorSource(major.source)
                .designSource(design.source)
                .build();
    }

    private static IntPick pickMin(int entranceValue, int entranceYear, int graduationValue, int graduationYear) {
        if (graduationValue < entranceValue) {
            return new IntPick(graduationValue, graduationYear + " 졸업예정(유리)");
        }
        if (entranceValue < graduationValue) {
            return new IntPick(entranceValue, entranceYear + "년도(입학·유리)");
        }
        return new IntPick(entranceValue, entranceYear + "년도(=졸업예정과 동일)");
    }

    private static DoublePick pickMin(double entranceValue, int entranceYear, double graduationValue, int graduationYear) {
        if (graduationValue < entranceValue - 1e-9) {
            return new DoublePick(graduationValue, graduationYear + " 졸업예정(유리)");
        }
        if (entranceValue < graduationValue - 1e-9) {
            return new DoublePick(entranceValue, entranceYear + "년도(입학·유리)");
        }
        return new DoublePick(entranceValue, entranceYear + "년도(=졸업예정과 동일)");
    }

    /**
     * 인증선택: 입학 연도에 요건이 없으면(2020~2021) 졸업 연도 요건을 끌어오지 않는다.
     * 둘 다 있으면 최소 과목/학점이 더 작은 쪽을 유리하다고 본다.
     */
    private static AbeekYearRequirement moreLenientCert(AbeekYearRequirement entrance, AbeekYearRequirement graduation) {
        if (entrance.getCertElectiveMinCourses() <= 0) {
            return entrance;
        }
        if (graduation.getCertElectiveMinCourses() <= 0) {
            return entrance;
        }
        int scoreEntrance = entrance.getCertElectiveMinCourses() * 100 + entrance.getCertElectiveMinCredits();
        int scoreGraduation = graduation.getCertElectiveMinCourses() * 100 + graduation.getCertElectiveMinCredits();
        return scoreGraduation < scoreEntrance ? graduation : entrance;
    }

    private record IntPick(int value, String source) {}
    private record DoublePick(double value, String source) {}
}
