package sejong.abeek.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sejong.abeek.domain.AbeekYearRequirement;
import sejong.abeek.domain.EffectiveAbeekRequirement;
import sejong.abeek.repository.AbeekYearRequirementRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvantageousRequirementServiceTest {

    @Mock
    private AbeekYearRequirementRepository requirementRepository;

    @InjectMocks
    private AdvantageousRequirementService service;

    @Test
    @DisplayName("입학 설계12 vs 졸업 설계10 → 유리한 10학점 적용")
    void designTakesLowerFromGraduation() {
        when(requirementRepository.findByDepartmentCodeAndYear("CSE", 2021)).thenReturn(Optional.of(req(2021, 60, 12)));
        when(requirementRepository.findByDepartmentCodeAndYear("CSE", 2026)).thenReturn(Optional.of(req(2026, 45, 10)));

        EffectiveAbeekRequirement effective = service.resolve(2021, 2026);

        assertThat(effective.getDesignMinCredits()).isEqualTo(10);
        assertThat(effective.getMajorMinCredits()).isEqualTo(45);
        assertThat(effective.getDesignSource()).contains("2026");
        assertThat(effective.getMajorSource()).contains("2026");
    }

    @Test
    @DisplayName("졸업 연도 설계가 더 높아지면 입학 연도 12학점 유지")
    void designKeepsEntranceWhenGraduationHigher() {
        when(requirementRepository.findByDepartmentCodeAndYear("CSE", 2021)).thenReturn(Optional.of(req(2021, 60, 12)));
        when(requirementRepository.findByDepartmentCodeAndYear("CSE", 2026)).thenReturn(Optional.of(req(2026, 60, 15)));

        EffectiveAbeekRequirement effective = service.resolve(2021, 2026);

        assertThat(effective.getDesignMinCredits()).isEqualTo(12);
        assertThat(effective.getDesignSource()).contains("2021");
    }

    private AbeekYearRequirement req(int year, int major, double design) {
        return AbeekYearRequirement.builder()
                .year(year)
                .generalMinCredits(14)
                .bsmMinCredits(18)
                .majorMinCredits(major)
                .designMinCredits(design)
                .certElectiveMinCourses(year >= 2022 ? 2 : 0)
                .certElectiveMinCredits(year >= 2022 ? 6 : 0)
                .build();
    }
}
