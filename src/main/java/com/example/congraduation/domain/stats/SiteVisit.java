package com.example.congraduation.domain.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일자별 순 방문자. 같은 visitorKey는 하루 1행만 유지한다.
 */
@Entity
@Table(
        name = "site_visits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_site_visits_visitor_date",
                columnNames = {"visitor_key", "visit_date"}
        )
)
public class SiteVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** student:{id} 또는 anon:{uuid} */
    @Column(name = "visitor_key", nullable = false, length = 80)
    private String visitorKey;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "first_visited_at", nullable = false)
    private LocalDateTime firstVisitedAt;

    @Column(name = "last_visited_at", nullable = false)
    private LocalDateTime lastVisitedAt;

    protected SiteVisit() {
    }

    private SiteVisit(
            String visitorKey,
            Long studentId,
            LocalDate visitDate,
            LocalDateTime visitedAt
    ) {
        this.visitorKey = visitorKey;
        this.studentId = studentId;
        this.visitDate = visitDate;
        this.firstVisitedAt = visitedAt;
        this.lastVisitedAt = visitedAt;
    }

    public static SiteVisit create(String visitorKey, Long studentId, LocalDate visitDate, LocalDateTime visitedAt) {
        return new SiteVisit(visitorKey, studentId, visitDate, visitedAt);
    }

    public void touch(LocalDateTime visitedAt, Long studentId) {
        this.lastVisitedAt = visitedAt;
        if (this.studentId == null && studentId != null) {
            this.studentId = studentId;
        }
    }

    public Long getId() {
        return id;
    }

    public String getVisitorKey() {
        return visitorKey;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public LocalDateTime getFirstVisitedAt() {
        return firstVisitedAt;
    }

    public LocalDateTime getLastVisitedAt() {
        return lastVisitedAt;
    }
}
