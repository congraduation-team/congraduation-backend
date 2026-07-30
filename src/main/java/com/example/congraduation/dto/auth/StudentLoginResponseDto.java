package com.example.congraduation.dto.auth;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.student.StudentMajorTrackResponseDto;
import java.util.List;

public class StudentLoginResponseDto {

    private Long id;
    private String studentNo;
    private String name;
    private String major;
    private MajorType majorType;
    private String secondaryMajor;
    private List<StudentMajorTrackResponseDto> tracks;
    private Integer gradeLevel;
    private Integer admissionYear;
    private String status;
    private boolean admin;

    public StudentLoginResponseDto(
            Long id,
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            List<StudentMajorTrackResponseDto> tracks,
            Integer gradeLevel,
            Integer admissionYear,
            String status,
            boolean admin
    ) {
        this.id = id;
        this.studentNo = studentNo;
        this.name = name;
        this.major = major;
        this.majorType = majorType;
        this.secondaryMajor = secondaryMajor;
        this.tracks = tracks;
        this.gradeLevel = gradeLevel;
        this.admissionYear = admissionYear;
        this.status = status;
        this.admin = admin;
    }

    public static StudentLoginResponseDto from(Student student) {
        return new StudentLoginResponseDto(
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                student.getMajor(),
                student.getMajorType(),
                student.getSecondaryMajor(),
                student.getMajorTracks().stream()
                        .map(StudentMajorTrackResponseDto::from)
                        .toList(),
                student.getGradeLevel(),
                student.getAdmissionYear(),
                student.getStatus(),
                student.isAdmin()
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

    public List<StudentMajorTrackResponseDto> getTracks() {
        return tracks;
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

    public boolean isAdmin() {
        return admin;
    }
}
