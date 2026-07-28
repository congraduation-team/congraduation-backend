package sejong.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sejong.abeek.domain.CourseMaster;

import java.util.Optional;

public interface CourseMasterRepository extends JpaRepository<CourseMaster, Long> {
    Optional<CourseMaster> findByCourseCode(String courseCode);
    Optional<CourseMaster> findByName(String name);
}
