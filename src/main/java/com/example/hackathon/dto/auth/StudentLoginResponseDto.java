package com.example.hackathon.dto.auth;

import com.example.hackathon.domain.MajorType;
import com.example.hackathon.domain.Student;

public class StudentLoginResponseDto {

    private Long id;
    private String studentNo;
    private String name;
    private String major;
    private MajorType majorType;
    private String secondaryMajor;
    private Integer gradeLevel;
    private Integer admissionYear;
    private String status;

    public StudentLoginResponseDto(
            Long id,
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            Integer gradeLevel,
            Integer admissionYear,
            String status
    ) {
        this.id = id;
        this.studentNo = studentNo;
        this.name = name;
        this.major = major;
        this.majorType = majorType;
        this.secondaryMajor = secondaryMajor;
        this.gradeLevel = gradeLevel;
        this.admissionYear = admissionYear;
        this.status = status;
    }

    public static StudentLoginResponseDto from(Student student) {
        return new StudentLoginResponseDto(
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                student.getMajor(),
                student.getMajorType(),
                student.getSecondaryMajor(),
                student.getGradeLevel(),
                student.getAdmissionYear(),
                student.getStatus()
        );
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
}
