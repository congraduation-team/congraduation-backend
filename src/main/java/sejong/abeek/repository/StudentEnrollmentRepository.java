package sejong.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sejong.abeek.domain.StudentEnrollment;

import java.util.List;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {
    List<StudentEnrollment> findByStudent_Id(Long studentId);
}
