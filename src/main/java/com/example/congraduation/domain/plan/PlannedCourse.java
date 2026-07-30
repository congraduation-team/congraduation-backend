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
@Table(name = "planned_courses")
public class PlannedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_semester_id")
    private PlannedSemester plannedSemester;

    @Column(nullable = false)
    private Integer targetYear;

    @Column(nullable = false)
    private Integer targetSemester;

    @Column(nullable = false, length = 20)
    private String courseCode;

    @Column(nullable = false, length = 255)
    private String courseName;

    @Column(length = 30)
    private String category;

    @Column(nullable = false, length = 20)
    private String credit;

    @Column(length = 10)
    private String expectedGrade;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PlannedCourse() {
    }

    private PlannedCourse(
            Student student,
            PlannedSemester plannedSemester,
            Integer targetYear,
            Integer targetSemester,
            String courseCode,
            String courseName,
            String category,
            String credit,
            String expectedGrade,
            LocalDateTime createdAt
    ) {
        this.student = student;
        this.plannedSemester = plannedSemester;
        this.targetYear = targetYear;
        this.targetSemester = targetSemester;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.category = category;
        this.credit = credit;
        this.expectedGrade = expectedGrade;
        this.createdAt = createdAt;
    }

    public static PlannedCourse create(
            Student student,
            PlannedSemester plannedSemester,
            Integer targetYear,
            Integer targetSemester,
            String courseCode,
            String courseName,
            String category,
            String credit,
            String expectedGrade
    ) {
        return new PlannedCourse(
                student,
                plannedSemester,
                targetYear,
                targetSemester,
                courseCode,
                courseName,
                category,
                credit,
                expectedGrade,
                LocalDateTime.now()
        );
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Integer getTargetYear() {
        return targetYear;
    }

    public Integer getTargetSemester() {
        return targetSemester;
    }

    public Long getPlannedSemesterId() {
        return plannedSemester == null ? null : plannedSemester.getId();
    }

    public Integer getGradeYear() {
        return plannedSemester == null ? targetYear : plannedSemester.getGradeYear();
    }

    public Integer getSemester() {
        return plannedSemester == null ? targetSemester : plannedSemester.getSemester();
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getCategory() {
        return category;
    }

    public String getCredit() {
        return credit;
    }

    public String getExpectedGrade() {
        return expectedGrade;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void updateExpectedGrade(String expectedGrade) {
        this.expectedGrade = expectedGrade;
    }
}
