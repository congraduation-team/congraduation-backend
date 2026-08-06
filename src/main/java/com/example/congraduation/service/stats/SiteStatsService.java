package com.example.congraduation.service.stats;

import com.example.congraduation.domain.stats.SiteVisit;
import com.example.congraduation.dto.stats.RecordVisitResponseDto;
import com.example.congraduation.dto.stats.SiteStatsResponseDto;
import com.example.congraduation.repository.stats.SiteVisitRepository;
import com.example.congraduation.repository.transcript.TranscriptUploadRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteStatsService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SiteVisitRepository siteVisitRepository;
    private final TranscriptUploadRepository transcriptUploadRepository;
    private final Clock clock;

    /**
     * Spring 주입용. Clock은 빈이 아니므로 2-arg만 @Autowired로 고정한다.
     * (3-arg 테스트 생성자가 있으면 Spring이 Clock 빈을 찾다 기동 실패할 수 있음)
     */
    @Autowired
    public SiteStatsService(
            SiteVisitRepository siteVisitRepository,
            TranscriptUploadRepository transcriptUploadRepository
    ) {
        this(siteVisitRepository, transcriptUploadRepository, Clock.system(ZONE));
    }

    /** 단위 테스트용. Spring 컨테이너에서는 사용하지 않는다. */
    SiteStatsService(
            SiteVisitRepository siteVisitRepository,
            TranscriptUploadRepository transcriptUploadRepository,
            Clock clock
    ) {
        this.siteVisitRepository = siteVisitRepository;
        this.transcriptUploadRepository = transcriptUploadRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SiteStatsResponseDto getSummary() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = YearMonth.from(today).atDay(1);
        return new SiteStatsResponseDto(
                siteVisitRepository.countByVisitDate(today),
                siteVisitRepository.countDistinctVisitorKeyBetween(monthStart, today),
                siteVisitRepository.countDistinctVisitorKey(),
                transcriptUploadRepository.countDistinctStudents(),
                ZONE.getId(),
                today.toString(),
                monthStart.toString()
        );
    }

    @Transactional
    public RecordVisitResponseDto recordVisit(String rawVisitorKey, Long studentId) {
        String visitorKey = resolveVisitorKey(rawVisitorKey, studentId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        return siteVisitRepository.findByVisitorKeyAndVisitDate(visitorKey, today)
                .map(existing -> {
                    existing.touch(now, studentId);
                    return new RecordVisitResponseDto(visitorKey, false);
                })
                .orElseGet(() -> {
                    siteVisitRepository.save(SiteVisit.create(visitorKey, studentId, today, now));
                    return new RecordVisitResponseDto(visitorKey, true);
                });
    }

    /** 로그인 성공 시 호출. student:{id}로 당일 방문자에 집계한다. */
    @Transactional
    public void recordStudentVisit(Long studentId) {
        if (studentId == null) {
            return;
        }
        recordVisit(null, studentId);
    }

    static String resolveVisitorKey(String rawVisitorKey, Long studentId) {
        if (studentId != null) {
            return "student:" + studentId;
        }
        if (rawVisitorKey == null || rawVisitorKey.isBlank()) {
            throw new IllegalArgumentException("visitorKey 또는 studentId 중 하나는 필수입니다.");
        }
        String trimmed = rawVisitorKey.trim();
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("visitorKey는 64자를 넘을 수 없습니다.");
        }
        if (trimmed.regionMatches(true, 0, "student:", 0, "student:".length())) {
            throw new IllegalArgumentException("익명 visitorKey는 student: 접두사를 쓸 수 없습니다.");
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("anon:")) {
            return "anon:" + normalized.substring("anon:".length());
        }
        // UUID 형태면 anon: 접두 부여
        try {
            UUID.fromString(trimmed);
            return "anon:" + normalized;
        } catch (IllegalArgumentException ignored) {
            return "anon:" + normalized;
        }
    }
}
