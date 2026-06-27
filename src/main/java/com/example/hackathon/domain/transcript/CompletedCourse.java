package com.example.hackathon.domain.transcript;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "completed_courses")
public class CompletedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transcript_upload_id", nullable = false)
    private TranscriptUpload transcriptUpload;

    @Column(nullable = false, length = 10)
    private String year;

    @Column(nullable = false, length = 20)
    private String semester;

    @Column(nullable = false, length = 20)
    private String courseCode;

    @Column(nullable = false, length = 255)
    private String courseName;

    @Column(length = 30)
    private String category;

    @Column(nullable = false, length = 20)
    private String credit;

    @Column(length = 20)
    private String evaluationMethod;

    @Column(length = 20)
    private String grade;

    @Column(nullable = false, length = 20)
    private String gradePoint;

    protected CompletedCourse() {
    }

    private CompletedCourse(
            String year,
            String semester,
            String courseCode,
            String courseName,
            String category,
            String credit,
            String evaluationMethod,
            String grade,
            String gradePoint
    ) {
        this.year = year;
        this.semester = semester;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.category = category;
        this.credit = credit;
        this.evaluationMethod = evaluationMethod;
        this.grade = grade;
        this.gradePoint = gradePoint;
    }

    public static CompletedCourse create(
            String year,
            String semester,
            String courseCode,
            String courseName,
            String category,
            String credit,
            String evaluationMethod,
            String grade,
            String gradePoint
    ) {
        return new CompletedCourse(year, semester, courseCode, courseName, category, credit, evaluationMethod, grade, gradePoint);
    }

    void assignTranscriptUpload(TranscriptUpload transcriptUpload) {
        this.transcriptUpload = transcriptUpload;
    }

    public String getYear() {
        return year;
    }

    public String getSemester() {
        return semester;
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

    public String getEvaluationMethod() {
        return evaluationMethod;
    }

    public String getGrade() {
        return grade;
    }

    public String getGradePoint() {
        return gradePoint;
    }
}
