package com.example.congraduation.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.dto.plan.PlannableCourseCatalogResponseDto;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.service.transcript.TranscriptStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlannableCourseCatalogServiceTest {

    @Test
    void returnsOnlyLatestSpringCoursesWhenSemesterIsOne() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);

        TimetableTermData spring2026 = new TimetableTermData(
                2026,
                1,
                List.of(offering("100001", "알고리즘", "전공필수"))
        );
        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(offering("100002", "컴퓨터네트워크", "전공선택"))
        );

        when(timetableCatalog.latestTermForSemester(1)).thenReturn(Optional.of(spring2026));
        when(timetableCatalog.availableTerms()).thenReturn(List.of(spring2026, fall2025));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                null,
                null,
                1,
                null,
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses()).extracting(course -> course.courseName())
                .containsExactly("알고리즘");
        assertThat(response.courses().getFirst().offeredTerms()).containsExactly("2026-1");
    }

    @Test
    void returnsOnlyExactTermWhenOfferedTermIsProvided() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);

        TimetableTermData fall2025 = new TimetableTermData(
                2025,
                2,
                List.of(offering("100002", "컴퓨터네트워크", "전공선택"))
        );

        when(timetableCatalog.findTerm(2025, 2)).thenReturn(Optional.of(fall2025));

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService
        );

        PlannableCourseCatalogResponseDto response = service.getCatalog(
                null,
                null,
                null,
                1,
                "2025-2",
                null,
                null
        );

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.courses().getFirst().courseName()).isEqualTo("컴퓨터네트워크");
        assertThat(response.courses().getFirst().offeredTerms()).containsExactly("2025-2");
    }

    @Test
    void rejectsUnsupportedSemesterValue() {
        TimetableCatalog timetableCatalog = mock(TimetableCatalog.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        TranscriptStorageService transcriptStorageService = mock(TranscriptStorageService.class);

        PlannableCourseCatalogService service = new PlannableCourseCatalogService(
                timetableCatalog,
                studentRepository,
                transcriptStorageService
        );

        assertThatThrownBy(() -> service.getCatalog(
                null,
                null,
                null,
                3,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semester는 1 또는 2만 가능합니다.");
    }

    private TimetableOffering offering(String courseCode, String courseName, String category) {
        return new TimetableOffering(
                "공과대학",
                "컴퓨터공학과",
                courseCode,
                "001",
                courseName,
                category,
                "3",
                3.0,
                "이론",
                "월 09:00~10:30",
                "집현관",
                "홍길동",
                "컴퓨터공학과"
        );
    }
}
