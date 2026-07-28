package com.example.congraduation.abeek.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 연도별 ABEEK 최소 이수학점 요건.
 */
@Entity
@Table(
        name = "abeek_year_requirement",
        uniqueConstraints = @UniqueConstraint(columnNames = {"department_code", "abeek_year"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbeekYearRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "abeek_year", nullable = false)
    private int year;

    @Column(name = "department_code", nullable = false, length = 32)
    @Builder.Default
    private String departmentCode = "CSE";

    @Column(nullable = false)
    private int generalMinCredits;

    @Column(nullable = false)
    private int bsmMinCredits;

    @Column(nullable = false)
    private int majorMinCredits;

    @Column(nullable = false)
    private double designMinCredits;

    /**
     * 인증선택: 지정 과목 중 최소 과목 수 (0이면 해당 없음)
     */
    @Column(nullable = false)
    @Builder.Default
    private int certElectiveMinCourses = 0;

    /**
     * 인증선택: 최소 학점
     */
    @Column(nullable = false)
    @Builder.Default
    private int certElectiveMinCredits = 0;

    /**
     * 인증선택: 최소 이수 영역 수 (권장/필수 구분용, 0이면 미적용)
     */
    @Column(nullable = false)
    @Builder.Default
    private int certElectiveMinAreas = 0;

    @Lob
    private String note;
}
