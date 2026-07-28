package sejong.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sejong.abeek.domain.AbeekStudent;

import java.util.Optional;

public interface AbeekStudentRepository extends JpaRepository<AbeekStudent, Long> {

    Optional<AbeekStudent> findByStudentId(String studentId);

    @Query("""
            select s from AbeekStudent s
            left join fetch s.enrollments e
            left join fetch e.courseMaster
            where s.studentId = :studentId
            """)
    Optional<AbeekStudent> findWithEnrollmentsByStudentId(@Param("studentId") String studentId);
}
