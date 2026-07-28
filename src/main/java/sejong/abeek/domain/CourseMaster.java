package sejong.abeek.domain;

import jakarta.persistence.*;
import lombok.*;
import sejong.abeek.domain.enums.CourseCategory;
import sejong.abeek.domain.enums.DesignLevel;
import sejong.abeek.domain.enums.ElectiveArea;

/**
 * 과목 마스터 (연도와 무관한 식별).
 * equivalenceGroup: 연도별 과목명 변경 시 동일 과목으로 매칭 (예: 기초미적분학 ↔ 미적분학1)
 */
@Entity
@Table(name = "course_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 내부 코드 (예: CSE101, BSM_CALC) */
    @Column(nullable = false, unique = true, length = 64)
    private String courseCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CourseCategory category;

    /**
     * 동등 과목 그룹. 같은 그룹이면 수강 시 동일 요건으로 인정.
     */
    @Column(length = 64)
    private String equivalenceGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ElectiveArea electiveArea = ElectiveArea.NONE;

    @Column(nullable = false)
    @Builder.Default
    private boolean departmentCourse = true;
}
