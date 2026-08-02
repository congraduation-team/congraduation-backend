package com.example.congraduation.repository.plan;

import com.example.congraduation.domain.plan.PlannedSemester;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedSemesterRepository extends JpaRepository<PlannedSemester, Long> {

    List<PlannedSemester> findAllByStudentIdOrderByGradeYearAscSemesterAscCreatedAtAsc(Long studentId);

    Optional<PlannedSemester> findByIdAndStudentId(Long id, Long studentId);

    Optional<PlannedSemester> findTopByStudentIdAndGradeYearAndSemesterOrderByCreatedAtAscIdAsc(
            Long studentId,
            Integer gradeYear,
            Integer semester
    );

    void deleteByIdAndStudentId(Long id, Long studentId);
}
