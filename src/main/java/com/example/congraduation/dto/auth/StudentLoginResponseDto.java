package com.example.congraduation.dto.auth;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.auth.JwtService.JwtTokenDto;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.sejong.SejongEnglishCertificationResponseDto;
import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;
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
    private SejongReadingStatusResponseDto readingStatus;
    private SejongEnglishCertificationResponseDto englishCertification;
    private String accessToken;
    private String tokenType;
    private Long tokenExpiresAt;

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
            boolean admin,
            SejongReadingStatusResponseDto readingStatus,
            SejongEnglishCertificationResponseDto englishCertification,
            String accessToken,
            String tokenType,
            Long tokenExpiresAt
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
        this.readingStatus = readingStatus;
        this.englishCertification = englishCertification;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public static StudentLoginResponseDto from(
            Student student,
            SejongReadingStatusResponseDto readingStatus,
            SejongEnglishCertificationResponseDto englishCertification,
            JwtTokenDto jwtToken
    ) {
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
                student.isAdmin(),
                readingStatus,
                englishCertification,
                jwtToken == null ? null : jwtToken.accessToken(),
                jwtToken == null ? null : jwtToken.tokenType(),
                jwtToken == null ? null : jwtToken.expiresAt()
        );
    }

    public static StudentLoginResponseDto from(Student student) {
        return from(student, null, null, null);
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

    public SejongReadingStatusResponseDto getReadingStatus() {
        return readingStatus;
    }

    public SejongEnglishCertificationResponseDto getEnglishCertification() {
        return englishCertification;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Long getTokenExpiresAt() {
        return tokenExpiresAt;
    }
}
