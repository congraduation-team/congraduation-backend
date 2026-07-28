package sejong.abeek.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateStudentRequest {

    @NotBlank
    private String studentId;

    @NotBlank
    private String name;

    @Min(2015)
    @Max(2030)
    private int entranceYear;

    /**
     * 졸업 ABEEK 적용 연도.
     * 2027년 2월 졸업(막학기 2026-2)이면 2026.
     */
    @Min(2015)
    @Max(2035)
    private int graduationAbeekYear;

    private String department = "컴퓨터공학과";

    private String departmentCode = "CSE";

    @Valid
    private List<EnrollmentRequest> enrollments = new ArrayList<>();

    @Getter
    @Setter
    public static class EnrollmentRequest {
        @NotBlank
        private String courseCode;

        @NotNull
        @Min(1)
        private Integer credits;

        @DecimalMin("0.0")
        private double designCredits;

        @Min(2015)
        private int takenYear;

        @Min(1)
        @Max(2)
        private int takenSemester;

        private boolean passed = true;
    }
}
