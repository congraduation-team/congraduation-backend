package com.example.congraduation.repository.plan;

import com.example.congraduation.domain.plan.PlannedCourse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedCourseRepository extends JpaRepository<PlannedCourse, Long> {

    List<PlannedCourse> findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(Long studentId);

    List<PlannedCourse> findAllByPlannedSemester_IdOrderByCreatedAtAsc(Long plannedSemesterId);

    Optional<PlannedCourse> findByIdAndStudentId(Long id, Long studentId);

    void deleteByIdAndStudentId(Long id, Long studentId);
}
