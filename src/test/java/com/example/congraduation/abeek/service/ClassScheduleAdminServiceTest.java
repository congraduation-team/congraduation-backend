package com.example.congraduation.abeek.service;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableExcelParser;
import com.example.congraduation.dto.admin.AdminUploadResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClassScheduleAdminServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadCsvPersistsAndReplacesCatalog() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Path overrideDir = tempDir.resolve("override");
        Path resourcesDir = tempDir.resolve("resources");
        Files.createDirectories(resourcesDir);

        TimetableCatalog catalog = new TimetableCatalog(objectMapper, overrideDir.toString());
        // @PostConstruct 미호출 — 빈 카탈로그에서 시작
        ClassScheduleAdminService service = new ClassScheduleAdminService(
                new TimetableExcelParser(),
                catalog,
                objectMapper,
                overrideDir.toString(),
                resourcesDir.toString()
        );

        String csv = """
                순번,개설대학,개설학과전공,학수번호,분반,교과목명,이수구분,학년(학기),학점,이론,실습,수업유형,학점교류수강가능,요일 및 강의시간,강의실,메인교수명,주관학과
                1,공대,컴공,010001,001,알고리즘,전공선택,3,3.0,3,0,이론,,화 10:00~11:30,센101,김교수,컴공
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "2026-1.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        AdminUploadResponseDto response = service.upload(file, 2026, 1);

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.semester()).isEqualTo(1);
        assertThat(catalog.findTerm(2026, 1)).isPresent();
        assertThat(Files.exists(overrideDir.resolve("2026-1.json"))).isTrue();
        assertThat(Files.exists(resourcesDir.resolve("2026-1.json"))).isTrue();
    }
}
