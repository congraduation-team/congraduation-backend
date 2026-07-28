package sejong.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sejong.abeek.domain.AbeekYearRequirement;

import java.util.Optional;
import java.util.List;

public interface AbeekYearRequirementRepository extends JpaRepository<AbeekYearRequirement, Long> {
    Optional<AbeekYearRequirement> findByYear(int year);
    Optional<AbeekYearRequirement> findByDepartmentCodeAndYear(String departmentCode, int year);
    boolean existsByDepartmentCode(String departmentCode);
    @org.springframework.data.jpa.repository.Query("select distinct r.departmentCode from AbeekYearRequirement r order by r.departmentCode")
    List<String> findDistinctDepartmentCodes();
}
