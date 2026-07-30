package com.example.congraduation.repository.plan;

import com.example.congraduation.domain.plan.PlannedCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedCourseRepository extends JpaRepository<PlannedCourse, Long> {

    List<PlannedCourse> findAllByStudentIdOrderByTargetYearAscTargetSemesterAscCreatedAtAsc(Long studentId);

    void deleteByIdAndStudentId(Long id, Long studentId);
}
