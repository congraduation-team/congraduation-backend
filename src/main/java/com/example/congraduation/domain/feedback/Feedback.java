package com.example.congraduation.domain.feedback;

import com.example.congraduation.domain.Student;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackType type;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    /** 제출 시점 스냅샷 (학생 정보 변경·탈퇴 대비) */
    @Column(length = 20)
    private String studentNo;

    @Column(length = 100)
    private String studentName;

    @Column(length = 100)
    private String major;

    @Column(length = 1000)
    private String adminNote;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Feedback() {
    }

    private Feedback(
            FeedbackType type,
            String title,
            String content,
            FeedbackStatus status,
            Student student,
            String studentNo,
            String studentName,
            String major,
            LocalDateTime createdAt
    ) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.status = status;
        this.student = student;
        this.studentNo = studentNo;
        this.studentName = studentName;
        this.major = major;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Feedback create(
            FeedbackType type,
            String title,
            String content,
            Student student,
            String studentNo,
            String studentName,
            String major
    ) {
        return new Feedback(
                type,
                title,
                content,
                FeedbackStatus.OPEN,
                student,
                studentNo,
                studentName,
                major,
                LocalDateTime.now()
        );
    }

    public void updateAdmin(FeedbackStatus status, String adminNote) {
        if (status != null) {
            this.status = status;
        }
        if (adminNote != null) {
            this.adminNote = adminNote.isBlank() ? null : adminNote.trim();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public FeedbackType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public Student getStudent() {
        return student;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getMajor() {
        return major;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
