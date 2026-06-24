package com.example.hackathon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String studentNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String major;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MajorType majorType;

    @Column(length = 100)
    private String secondaryMajor;

    @Column(nullable = false)
    private Integer gradeLevel;

    @Column
    private Integer admissionYear;

    @Column(length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Student() {
    }

    public Student(
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            Integer gradeLevel,
            Integer admissionYear,
            String status,
            LocalDateTime createdAt
    ) {
        this.studentNo = studentNo;
        this.name = name;
        this.major = major;
        this.majorType = majorType;
        this.secondaryMajor = secondaryMajor;
        this.gradeLevel = gradeLevel;
        this.admissionYear = admissionYear;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Student create(
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            Integer gradeLevel,
            Integer admissionYear,
            String status
    ) {
        return new Student(
                studentNo,
                name,
                major,
                majorType,
                secondaryMajor,
                gradeLevel,
                admissionYear,
                status,
                LocalDateTime.now()
        );
    }

    public void updateAcademicInfo(
            String name,
            String major,
            Integer gradeLevel,
            Integer admissionYear,
            String status
    ) {
        this.name = name;
        this.major = major;
        this.gradeLevel = gradeLevel;
        this.admissionYear = admissionYear;
        this.status = status;
    }

    public void updateMajorTrack(MajorType majorType, String secondaryMajor) {
        this.majorType = majorType;
        this.secondaryMajor = secondaryMajor;
    }

    public Long getId() {
        return id;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getName() {
        return name;
    }

    public String getMajor() {
        return major;
    }

    public MajorType getMajorType() {
        return majorType;
    }

    public String getSecondaryMajor() {
        return secondaryMajor;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public Integer getAdmissionYear() {
        return admissionYear;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
