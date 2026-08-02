package com.example.congraduation.abeek.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.congraduation.abeek.domain.StudentEnrollment;

import java.util.List;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {
    List<StudentEnrollment> findByStudent_Id(Long studentId);

    void deleteByStudent_Id(Long studentId);
}
