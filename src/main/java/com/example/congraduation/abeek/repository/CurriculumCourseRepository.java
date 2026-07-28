package com.example.congraduation.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

import java.util.List;
import java.util.Optional;

public interface CurriculumCourseRepository extends JpaRepository<CurriculumCourse, Long> {

    List<CurriculumCourse> findByCurriculumYear(int curriculumYear);
    List<CurriculumCourse> findByDepartmentCodeAndCurriculumYear(String departmentCode, int curriculumYear);

    List<CurriculumCourse> findByCurriculumYearAndRole(int curriculumYear, CourseRole role);

    List<CurriculumCourse> findByCurriculumYearAndDesignLevel(int curriculumYear, DesignLevel designLevel);

    @Query("""
            select c from CurriculumCourse c
            join fetch c.courseMaster
            where c.curriculumYear = :year and c.departmentCode = :departmentCode
            """)
    List<CurriculumCourse> findAllWithMasterByDepartmentCodeAndYear(
            @Param("departmentCode") String departmentCode, @Param("year") int year);

    Optional<CurriculumCourse> findByCurriculumYearAndDepartmentCodeAndCourseMaster_CourseCode(
            int year, String departmentCode, String courseCode);
}
