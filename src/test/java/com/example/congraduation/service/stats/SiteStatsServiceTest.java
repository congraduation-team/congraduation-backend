package com.example.congraduation.service.stats;

import com.example.congraduation.domain.stats.SiteVisit;
import com.example.congraduation.dto.stats.RecordVisitResponseDto;
import com.example.congraduation.dto.stats.SiteStatsResponseDto;
import com.example.congraduation.repository.stats.SiteVisitRepository;
import com.example.congraduation.repository.transcript.TranscriptUploadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteStatsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private SiteVisitRepository siteVisitRepository;

    @Mock
    private TranscriptUploadRepository transcriptUploadRepository;

    private SiteStatsService service;
    private final AtomicLong idSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZONE); // KST 12:00
        service = new SiteStatsService(siteVisitRepository, transcriptUploadRepository, clock);
    }

    @Test
    void summaryUsesLoggedInVisitorsOnly() {
        LocalDate today = LocalDate.of(2026, 8, 5);
        LocalDate monthStart = LocalDate.of(2026, 8, 1);
        when(siteVisitRepository.countLoggedInByVisitDate(today)).thenReturn(12L);
        when(siteVisitRepository.countDistinctLoggedInVisitorKeyBetween(monthStart, today)).thenReturn(80L);
        when(siteVisitRepository.countDistinctLoggedInVisitorKey()).thenReturn(400L);
        when(transcriptUploadRepository.countDistinctStudents()).thenReturn(250L);

        SiteStatsResponseDto summary = service.getSummary();

        assertThat(summary.todayVisitors()).isEqualTo(12L);
        assertThat(summary.monthlyVisitors()).isEqualTo(80L);
        assertThat(summary.totalVisitors()).isEqualTo(400L);
        assertThat(summary.transcriptUsers()).isEqualTo(250L);
        assertThat(summary.timezone()).isEqualTo("Asia/Seoul");
        assertThat(summary.today()).isEqualTo("2026-08-05");
        assertThat(summary.monthStart()).isEqualTo("2026-08-01");
    }

    @Test
    void recordVisitCreatesOncePerDayForLoggedInStudent() {
        when(siteVisitRepository.findByVisitorKeyAndVisitDate("student:12", LocalDate.of(2026, 8, 5)))
                .thenReturn(Optional.empty());
        when(siteVisitRepository.save(any(SiteVisit.class))).thenAnswer(invocation -> {
            SiteVisit visit = invocation.getArgument(0);
            setId(visit, idSeq.getAndIncrement());
            return visit;
        });

        RecordVisitResponseDto first = service.recordVisit(null, 12L);
        assertThat(first.newlyCountedToday()).isTrue();
        assertThat(first.visitorKey()).isEqualTo("student:12");

        SiteVisit existing = SiteVisit.create(
                "student:12",
                12L,
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5).atTime(9, 0)
        );
        setId(existing, 9L);
        when(siteVisitRepository.findByVisitorKeyAndVisitDate("student:12", LocalDate.of(2026, 8, 5)))
                .thenReturn(Optional.of(existing));

        RecordVisitResponseDto second = service.recordVisit(null, 12L);
        assertThat(second.newlyCountedToday()).isFalse();
        verify(siteVisitRepository).save(any(SiteVisit.class));
    }

    @Test
    void skipsAnonymousVisitWithoutStudentId() {
        RecordVisitResponseDto result = service.recordVisit("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", null);

        assertThat(result.visitorKey()).isEmpty();
        assertThat(result.newlyCountedToday()).isFalse();
        verify(siteVisitRepository, never()).save(any());
    }

    @Test
    void studentIdTakesPrecedenceOverAnonKey() {
        when(siteVisitRepository.findByVisitorKeyAndVisitDate("student:12", LocalDate.of(2026, 8, 5)))
                .thenReturn(Optional.empty());
        when(siteVisitRepository.save(any(SiteVisit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordVisitResponseDto result = service.recordVisit("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", 12L);

        assertThat(result.visitorKey()).isEqualTo("student:12");
        ArgumentCaptor<SiteVisit> captor = ArgumentCaptor.forClass(SiteVisit.class);
        verify(siteVisitRepository).save(captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(12L);
    }

    private static void setId(Object target, long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
