package sejong.abeek.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sejong.abeek.repository.AbeekYearRequirementRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DepartmentController {

    private final AbeekYearRequirementRepository requirementRepository;

    @GetMapping("/api/departments")
    public List<String> departments() {
        return requirementRepository.findDistinctDepartmentCodes();
    }
}
