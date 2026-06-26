package com.example.hackathon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_major_tracks")
public class StudentMajorTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MajorType trackType;

    @Column(nullable = false, length = 100)
    private String departmentCode;

    @Column
    private Integer approvedAtSemester;

    @Column(nullable = false)
    private Boolean teachingCert;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected StudentMajorTrack() {
    }

    private StudentMajorTrack(
            MajorType trackType,
            String departmentCode,
            Integer approvedAtSemester,
            Boolean teachingCert,
            LocalDateTime createdAt
    ) {
        this.trackType = trackType;
        this.departmentCode = departmentCode;
        this.approvedAtSemester = approvedAtSemester;
        this.teachingCert = teachingCert;
        this.createdAt = createdAt;
    }

    public static StudentMajorTrack create(
            MajorType trackType,
            String departmentCode,
            Integer approvedAtSemester,
            Boolean teachingCert
    ) {
        return new StudentMajorTrack(
                trackType,
                departmentCode,
                approvedAtSemester,
                teachingCert != null ? teachingCert : false,
                LocalDateTime.now()
        );
    }

    void assignStudent(Student student) {
        this.student = student;
    }

    public Long getId() {
        return id;
    }

    public MajorType getTrackType() {
        return trackType;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public Integer getApprovedAtSemester() {
        return approvedAtSemester;
    }

    public Boolean getTeachingCert() {
        return teachingCert;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
