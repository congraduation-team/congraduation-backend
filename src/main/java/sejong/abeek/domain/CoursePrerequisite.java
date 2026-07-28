package sejong.abeek.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_prerequisite",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "department_code", "curriculum_year", "from_course_code", "to_course_code", "prerequisite_type"
        }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoursePrerequisite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_code", nullable = false, length = 32)
    private String departmentCode;

    @Column(name = "curriculum_year", nullable = false)
    private int year;

    @Column(name = "from_course_code", nullable = false, length = 128)
    private String fromCourseCode;

    @Column(name = "to_course_code", nullable = false, length = 128)
    private String toCourseCode;

    @Column(name = "prerequisite_type", nullable = false, length = 32)
    private String type;

    @Column(nullable = false)
    private boolean needsReview;
}
