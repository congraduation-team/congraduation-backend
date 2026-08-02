package com.example.congraduation.abeek.timetable;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimetableExcelParserTest {

    private final TimetableExcelParser parser = new TimetableExcelParser();

    @Test
    void parsesSejongStyleXlsx() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            String[] headers = {
                    "순번", "개설대학", "개설학과전공", "학수번호", "분반", "교과목명", "이수구분",
                    "학년\n(학기)", "학점", "이론", "실습", "수업\n유형", "학점교류\n수강가능",
                    "요일 및 강의시간", "강의실", "메인\n교수명", "주관학과"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue("소프트웨어융합대학");
            row.createCell(2).setCellValue("컴퓨터공학과");
            row.createCell(3).setCellValue("010123");
            row.createCell(4).setCellValue("001");
            row.createCell(5).setCellValue("자료구조및실습");
            row.createCell(6).setCellValue("전공필수");
            row.createCell(7).setCellValue("2");
            row.createCell(8).setCellValue(3.0);
            row.createCell(11).setCellValue("이론");
            row.createCell(13).setCellValue("월 09:00~10:30");
            row.createCell(14).setCellValue("센B101");
            row.createCell(15).setCellValue("홍길동");
            row.createCell(16).setCellValue("컴퓨터공학과");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        List<TimetableOffering> offerings = parser.parse(
                new MockMultipartFile("file", "timetable.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)
        );

        assertThat(offerings).hasSize(1);
        TimetableOffering o = offerings.getFirst();
        assertThat(o.courseCode()).isEqualTo("010123");
        assertThat(o.courseName()).isEqualTo("자료구조및실습");
        assertThat(o.openingDepartment()).isEqualTo("컴퓨터공학과");
        assertThat(o.credits()).isEqualTo(3.0);
        assertThat(o.professor()).isEqualTo("홍길동");
        assertThat(o.classType()).isEqualTo("이론");
    }

    @Test
    void parsesCsv() {
        String csv = """
                순번,개설대학,개설학과전공,학수번호,분반,교과목명,이수구분,학년(학기),학점,이론,실습,수업유형,학점교류수강가능,요일 및 강의시간,강의실,메인교수명,주관학과
                1,공대,컴공,010001,001,알고리즘,전공선택,3,3.0,3,0,이론,,화 10:00~11:30,센101,김교수,컴공
                """;
        List<TimetableOffering> offerings = parser.parse(
                new MockMultipartFile("file", "t.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))
        );
        assertThat(offerings).hasSize(1);
        assertThat(offerings.getFirst().courseName()).isEqualTo("알고리즘");
        assertThat(offerings.getFirst().schedule()).isEqualTo("화 10:00~11:30");
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> parser.parse(
                new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xlsx");
    }
}
