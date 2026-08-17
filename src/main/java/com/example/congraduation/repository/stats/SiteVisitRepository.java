package com.example.congraduation.repository.stats;

import com.example.congraduation.domain.stats.SiteVisit;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, Long> {

    Optional<SiteVisit> findByVisitorKeyAndVisitDate(String visitorKey, LocalDate visitDate);

    @Query("""
            select count(v)
            from SiteVisit v
            where v.visitDate = :visitDate
              and v.visitorKey like 'student:%'
            """)
    long countLoggedInByVisitDate(@Param("visitDate") LocalDate visitDate);

    @Query("""
            select count(distinct v.visitorKey)
            from SiteVisit v
            where v.visitDate >= :fromDate and v.visitDate <= :toDate
              and v.visitorKey like 'student:%'
            """)
    long countDistinctLoggedInVisitorKeyBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select count(distinct v.visitorKey)
            from SiteVisit v
            where v.visitorKey like 'student:%'
            """)
    long countDistinctLoggedInVisitorKey();
}
