package sejong.abeek.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AbeekEvaluationResponse {
    private final String studentId;
    private final String studentName;
    private final int entranceYear;
    private final int graduationAbeekYear;
    private final boolean overallSatisfied;

    private final CategoryProgressDto general;
    private final CategoryProgressDto bsm;
    private final CategoryProgressDto major;
    private final CategoryProgressDto design;

    private final boolean designSequenceSatisfied;
    private final DesignEvaluationResult designDetail;

    private final List<RequiredCourseStatusDto> entranceRequiredCourses;
    private final List<RequiredCourseStatusDto> waivedGraduationOnlyCourses;

    private final List<String> messages;
}
