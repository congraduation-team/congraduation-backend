package sejong.abeek.dto;

import lombok.Builder;
import lombok.Getter;
import sejong.abeek.domain.enums.CourseCategory;
import sejong.abeek.domain.enums.CourseRole;
import sejong.abeek.domain.enums.DesignLevel;
import sejong.abeek.domain.enums.ElectiveArea;

@Getter
@Builder
public class CurriculumCourseDto {
    private final String departmentCode;
    private final int curriculumYear;
    private final String courseCode;
    private final String courseName;
    private final CourseCategory category;
    private final CourseRole role;
    private final int credits;
    private final double designCredits;
    private final DesignLevel designLevel;
    private final ElectiveArea electiveArea;
    private final String recommendedTerm;
    private final boolean newlyIntroducedRequired;
}
