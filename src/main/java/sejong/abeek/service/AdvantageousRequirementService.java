package sejong.abeek.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sejong.abeek.domain.AbeekYearRequirement;
import sejong.abeek.domain.EffectiveAbeekRequirement;
import sejong.abeek.repository.AbeekYearRequirementRepository;

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
            return new IntPick(graduationValue, graduationYear + "년도(유리)");
        }
        if (entranceValue < graduationValue) {
            return new IntPick(entranceValue, entranceYear + "년도(입학·유리)");
        }
        return new IntPick(entranceValue, entranceYear + "년도(=졸업연도와 동일)");
    }

    private static DoublePick pickMin(double entranceValue, int entranceYear, double graduationValue, int graduationYear) {
        if (graduationValue < entranceValue - 1e-9) {
            return new DoublePick(graduationValue, graduationYear + "년도(유리)");
        }
        if (entranceValue < graduationValue - 1e-9) {
            return new DoublePick(entranceValue, entranceYear + "년도(입학·유리)");
        }
        return new DoublePick(entranceValue, entranceYear + "년도(=졸업연도와 동일)");
    }

    /**
     * 인증선택 최소 과목/학점이 더 작은 쪽을 유리하다고 본다.
     * 둘 다 0이면 입학 연도.
     */
    private static AbeekYearRequirement moreLenientCert(AbeekYearRequirement a, AbeekYearRequirement b) {
        int scoreA = a.getCertElectiveMinCourses() * 100 + a.getCertElectiveMinCredits();
        int scoreB = b.getCertElectiveMinCourses() * 100 + b.getCertElectiveMinCredits();
        // 0(요건 없음)은 구조가 다르므로 입학 연도 유지
        if (a.getCertElectiveMinCourses() == 0 && b.getCertElectiveMinCourses() > 0) {
            return a;
        }
        if (b.getCertElectiveMinCourses() == 0 && a.getCertElectiveMinCourses() > 0) {
            return a; // 입학에 선택요건이 있으면 입학 기준 유지
        }
        return scoreB < scoreA ? b : a;
    }

    private record IntPick(int value, String source) {}
    private record DoublePick(double value, String source) {}
}
