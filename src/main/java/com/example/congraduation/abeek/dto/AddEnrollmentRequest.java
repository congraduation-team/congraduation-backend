package com.example.congraduation.abeek.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddEnrollmentRequest {
    @NotBlank
    private String courseCode;

    @Min(1)
    private int credits;

    @DecimalMin("0.0")
    private double designCredits;

    @Min(2015)
    private int takenYear;

    @Min(1)
    @Max(2)
    private int takenSemester;

    private boolean passed = true;
}
