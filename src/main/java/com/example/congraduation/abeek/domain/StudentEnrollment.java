package com.example.congraduation.abeek.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 학생 수강 이력.
 * designCreditsApplied: 수강 당시(개설 연도) 설계학점 — 내부규정에 따라 개설연도 기준.
 */
@Entity
@Table(
        name = "student_enrollment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_master_id", "taken_year", "taken_semester"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AbeekStudent student;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "course_master_id", nullable = false)
    private CourseMaster courseMaster;

    /** 총 취득 학점 */
    @Column(nullable = false)
    private int credits;

    /** 수강 당시 설계학점 */
    @Column(nullable = false)
    @Builder.Default
    private double designCredits = 0;

    /** 수강 연도 (예: 2023) */
    @Column(name = "taken_year", nullable = false)
    private int takenYear;

    /** 학기 1 또는 2 */
    @Column(name = "taken_semester", nullable = false)
    private int takenSemester;

    /** 통과 여부 (F/NP 제외) */
    @Column(nullable = false)
    @Builder.Default
    private boolean passed = true;

    /** 비교용 학기 키: year*10 + semester (예: 2023-1 → 20231) */
    public int termKey() {
        return takenYear * 10 + takenSemester;
    }
}
