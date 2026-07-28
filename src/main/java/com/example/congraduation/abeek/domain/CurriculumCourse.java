package com.example.congraduation.abeek.domain;

import jakarta.persistence.*;
import lombok.*;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

/**
 * 특정 입학/교육과정 연도에 개설된 과목 정의 (학점·설계학점·역할).
 */
@Entity
@Table(
        name = "curriculum_course",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curriculum_year", "department_code", "course_master_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurriculumCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 교육과정/이수체계도 연도 (예: 2021) */
    @Column(name = "curriculum_year", nullable = false)
    private int curriculumYear;

    @Column(name = "department_code", nullable = false, length = 32)
    @Builder.Default
    private String departmentCode = "CSE";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_master_id", nullable = false)
    private CourseMaster courseMaster;

    /** 총 학점 */
    @Column(nullable = false)
    private int credits;

    /** 설계학점 (0이면 설계 아님) */
    @Column(nullable = false)
    @Builder.Default
    private double designCredits = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private DesignLevel designLevel = DesignLevel.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_role", nullable = false, length = 32)
    private CourseRole role;

    /** 권장 학년학기 표시용 (예: 2-1). 평가에는 미사용 */
    @Column(length = 8)
    private String recommendedTerm;

    /** 해당 연도에 신설된 필수 과목 여부 (입학 연도에 없으면 면제 대상) */
    @Column(nullable = false)
    @Builder.Default
    private boolean newlyIntroducedRequired = false;
}
