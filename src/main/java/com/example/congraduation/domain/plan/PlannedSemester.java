package com.example.congraduation.domain.plan;

import com.example.congraduation.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "planned_semesters")
public class PlannedSemester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private Integer gradeYear;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PlannedSemester() {
    }

    private PlannedSemester(Student student, Integer gradeYear, Integer semester, LocalDateTime createdAt) {
        this.student = student;
        this.gradeYear = gradeYear;
        this.semester = semester;
        this.createdAt = createdAt;
    }

    public static PlannedSemester create(Student student, Integer gradeYear, Integer semester) {
        return new PlannedSemester(student, gradeYear, semester, LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Integer getGradeYear() {
        return gradeYear;
    }

    public Integer getSemester() {
        return semester;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
