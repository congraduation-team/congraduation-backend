package com.example.congraduation.abeek;

import com.example.congraduation.CongraduationApplication;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.abeek.service.AbeekTranscriptEvaluationService;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성적표 기반 ABEEK 평가 1회가 발생시키는 JDBC 문장 수를 고정한다.
 * 과목 수에 비례해 커리큘럼 조회가 늘어나면 이 테스트가 먼저 깨진다.
 */
@SpringBootTest(classes = CongraduationApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AbeekTranscriptEvaluationQueryCountTest {

    /** 커리큘럼 조회는 과목 수가 아니라 살펴보는 연도 수(최대 9)에만 비례해야 한다. */
    private static final int MAX_SELECTS = 20;

    @Autowired
    private AbeekTranscriptEvaluationService evaluationService;

    @Autowired
    private CurriculumCourseRepository curriculumCourseRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("커리큘럼 조회 수는 성적표 과목 수에 비례해 증가하지 않는다")
    void selectCountDoesNotScaleWithCourseCount() {
        List<CompletedCourseUploadRowDto> rows = transcriptRows();
        assertThat(rows).hasSizeGreaterThan(20);

        long selects = measureSelects("21012345", rows);
        long doubledSelects = measureSelects("21012346", doubled(rows));

        assertThat(selects).isLessThanOrEqualTo(MAX_SELECTS);
        assertThat(doubledSelects).isLessThanOrEqualTo(selects);
    }

    private long measureSelects(String studentId, List<CompletedCourseUploadRowDto> rows) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        evaluationService.evaluateFromRows(rows, studentId, "홍길동", 2021, 2026, "CSE", null);

        long statements = statistics.getPrepareStatementCount();
        long writes = statistics.getEntityInsertCount()
                + statistics.getEntityUpdateCount()
                + statistics.getEntityDeleteCount();
        long selects = statements - writes;
        System.out.printf(
                "[query-count] rows=%d statements=%d writes=%d selects=%d%n",
                rows.size(), statements, writes, selects);
        return selects;
    }

    /** 같은 과목을 다른 학기로 한 번 더 이수한 성적표. 행 수만 두 배가 된다. */
    private List<CompletedCourseUploadRowDto> doubled(List<CompletedCourseUploadRowDto> rows) {
        List<CompletedCourseUploadRowDto> doubled = new ArrayList<>(rows);
        for (CompletedCourseUploadRowDto row : rows) {
            doubled.add(new CompletedCourseUploadRowDto(
                    row.year(),
                    row.semester().startsWith("1") ? "2학기" : "1학기",
                    row.courseCode(),
                    row.courseName(),
                    row.category(),
                    row.credit(),
                    row.evaluationMethod(),
                    row.grade(),
                    row.gradePoint()
            ));
        }
        return doubled;
    }

    private List<CompletedCourseUploadRowDto> transcriptRows() {
        List<CurriculumCourse> courses =
                curriculumCourseRepository.findAllWithMasterByDepartmentCodeAndYear("CSE", 2021);
        List<CompletedCourseUploadRowDto> rows = new ArrayList<>();
        int index = 0;
        for (CurriculumCourse course : courses) {
            int takenYear = 2021 + (index / 8) % 4;
            int takenSemester = index % 2 == 0 ? 1 : 2;
            rows.add(new CompletedCourseUploadRowDto(
                    String.valueOf(takenYear),
                    takenSemester + "학기",
                    null,
                    course.getCourseMaster().getName(),
                    "전필",
                    "3",
                    "GRADE",
                    "A+",
                    "4.5"
            ));
            index++;
        }
        return rows;
    }
}
