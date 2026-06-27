package com.example.hackathon.domain.transcript;

import com.example.hackathon.domain.Student;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transcript_uploads")
public class TranscriptUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @OneToMany(mappedBy = "transcriptUpload", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CompletedCourse> completedCourses = new ArrayList<>();

    protected TranscriptUpload() {
    }

    private TranscriptUpload(Student student, String originalFilename, LocalDateTime uploadedAt) {
        this.student = student;
        this.originalFilename = originalFilename;
        this.uploadedAt = uploadedAt;
    }

    public static TranscriptUpload create(Student student, String originalFilename) {
        return new TranscriptUpload(student, originalFilename, LocalDateTime.now());
    }

    public void addCompletedCourse(CompletedCourse completedCourse) {
        completedCourses.add(completedCourse);
        completedCourse.assignTranscriptUpload(this);
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public List<CompletedCourse> getCompletedCourses() {
        return completedCourses;
    }
}
