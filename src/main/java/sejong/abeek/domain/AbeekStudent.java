package sejong.abeek.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "abeek_student")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbeekStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_no", nullable = false, unique = true, length = 32)
    private String studentId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 입학 연도 (예: 2021) — 입학 당시 공학인증/교과과정의 기준 */
    @Column(nullable = false)
    private int entranceYear;

    /**
     * 졸업(예정) 연도의 공학인증 적용 연도.
     * 예: 2027년 2월 졸업 → 막학기 2026-2 → graduationAbeekYear = 2026
     */
    @Column(nullable = false)
    private int graduationAbeekYear;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String department = "컴퓨터공학과";

    @Column(name = "department_code", nullable = false, length = 32)
    @Builder.Default
    private String departmentCode = "CSE";

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudentEnrollment> enrollments = new ArrayList<>();

    public void addEnrollment(StudentEnrollment enrollment) {
        enrollments.add(enrollment);
        enrollment.setStudent(this);
    }
}
