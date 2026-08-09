package com.example.congraduation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private Integer completedSemesterCount;

    @Column
    private Integer admissionYear;

    @Column(length = 20)
    private String status;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean admin;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean englishCertificationSubmitted;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean englishCertificationCertified;

    @Column(length = 50)
    private String englishCertificationStatus;

    @Column(length = 100)
    private String englishCertificationExamType;

    @Column(length = 100)
    private String englishCertificationScore;

    @Column(length = 50)
    private String englishCertificationSubmittedAt;

    @Column
    private LocalDateTime englishCertificationCrawledAt;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean classicReadingCertified;

    @Column
    private Integer classicReadingCompletedCount;

    @Column
    private Integer classicReadingCertifiedCount;

    @Column
    private Integer classicReadingRequiredCount;

    @Column
    private LocalDateTime classicReadingCrawledAt;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<StudentMajorTrack> majorTracks = new ArrayList<>();

    protected Student() {
    }

    public Student(
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            Integer gradeLevel,
            Integer completedSemesterCount,
            Integer admissionYear,
            String status,
            boolean admin,
            LocalDateTime createdAt
    ) {
        this.studentNo = studentNo;
        this.name = name;
        this.major = major;
        this.majorType = majorType;
        this.secondaryMajor = secondaryMajor;
        this.gradeLevel = gradeLevel;
        this.completedSemesterCount = completedSemesterCount;
        this.admissionYear = admissionYear;
        this.status = status;
        this.admin = admin;
        this.createdAt = createdAt;
    }

    public static Student create(
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            Integer gradeLevel,
            Integer completedSemesterCount,
            Integer admissionYear,
            String status,
            boolean admin
    ) {
        return new Student(
                studentNo,
                name,
                major,
                majorType,
                secondaryMajor,
                gradeLevel,
                completedSemesterCount,
                admissionYear,
                status,
                admin,
                LocalDateTime.now()
        );
    }

    public static Student create(
            String studentNo,
            String name,
            String major,
            MajorType majorType,
            String secondaryMajor,
            Integer gradeLevel,
            Integer admissionYear,
            String status,
            boolean admin
    ) {
        return create(
                studentNo,
                name,
                major,
                majorType,
                secondaryMajor,
                gradeLevel,
                null,
                admissionYear,
                status,
                admin
        );
    }

    public void updateAcademicInfo(
            String name,
            String major,
            Integer gradeLevel,
            Integer completedSemesterCount,
            Integer admissionYear,
            String status
    ) {
        this.name = name;
        this.major = major;
        this.gradeLevel = gradeLevel;
        this.completedSemesterCount = completedSemesterCount;
        this.admissionYear = admissionYear;
        this.status = status;
    }

    public void updateMajorTrackSummary(MajorType majorType, String secondaryMajor) {
        this.majorType = majorType;
        this.secondaryMajor = secondaryMajor;
    }

    public void updateEnglishCertificationInfo(
            boolean submitted,
            boolean certified,
            String status,
            String examType,
            String score,
            String submittedAt
    ) {
        this.englishCertificationSubmitted = submitted;
        this.englishCertificationCertified = certified;
        this.englishCertificationStatus = status;
        this.englishCertificationExamType = examType;
        this.englishCertificationScore = score;
        this.englishCertificationSubmittedAt = submittedAt;
        this.englishCertificationCrawledAt = LocalDateTime.now();
    }

    public void updateClassicReadingCertificationInfo(
            boolean certified,
            Integer completedCount,
            Integer certifiedCount,
            Integer requiredCount
    ) {
        this.classicReadingCertified = certified;
        this.classicReadingCompletedCount = completedCount;
        this.classicReadingCertifiedCount = certifiedCount;
        this.classicReadingRequiredCount = requiredCount;
        this.classicReadingCrawledAt = LocalDateTime.now();
    }

    public void replaceMajorTracks(List<StudentMajorTrack> tracks) {
        this.majorTracks.clear();
        for (StudentMajorTrack track : tracks) {
            addMajorTrack(track);
        }
    }

    public void addMajorTrack(StudentMajorTrack track) {
        track.assignStudent(this);
        this.majorTracks.add(track);
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

    public Integer getCompletedSemesterCount() {
        return completedSemesterCount;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isEnglishCertificationSubmitted() {
        return englishCertificationSubmitted;
    }

    public boolean isEnglishCertificationCertified() {
        return englishCertificationCertified;
    }

    public String getEnglishCertificationStatus() {
        return englishCertificationStatus;
    }

    public String getEnglishCertificationExamType() {
        return englishCertificationExamType;
    }

    public String getEnglishCertificationScore() {
        return englishCertificationScore;
    }

    public String getEnglishCertificationSubmittedAt() {
        return englishCertificationSubmittedAt;
    }

    public LocalDateTime getEnglishCertificationCrawledAt() {
        return englishCertificationCrawledAt;
    }

    public boolean isClassicReadingCertified() {
        return classicReadingCertified;
    }

    public Integer getClassicReadingCompletedCount() {
        return classicReadingCompletedCount;
    }

    public Integer getClassicReadingCertifiedCount() {
        return classicReadingCertifiedCount;
    }

    public Integer getClassicReadingRequiredCount() {
        return classicReadingRequiredCount;
    }

    public LocalDateTime getClassicReadingCrawledAt() {
        return classicReadingCrawledAt;
    }

    public List<StudentMajorTrack> getMajorTracks() {
        return Collections.unmodifiableList(majorTracks);
    }
}
