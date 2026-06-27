package com.example.hackathon.repository.student;

import com.example.hackathon.domain.Student;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentNo(String studentNo);
}
