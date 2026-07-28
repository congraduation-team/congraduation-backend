package com.example.congraduation.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.congraduation.abeek.domain.CoursePrerequisite;

import java.util.Optional;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {
    Optional<CoursePrerequisite> findByDepartmentCodeAndYearAndFromCourseCodeAndToCourseCodeAndType(
            String departmentCode, int year, String fromCourseCode, String toCourseCode, String type);
}
