package com.example.hackathon.service.transcript;

import com.example.hackathon.dto.transcript.CompletedCourseUploadRowDto;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptExcelParser {

    private static final String SHEET_NAME = "기이수성적";
    private static final int DATA_START_ROW_INDEX = 4;

    public List<CompletedCourseUploadRowDto> parse(MultipartFile file) {
        validateFile(file);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("기이수성적 시트를 찾을 수 없습니다.");
            }

            DataFormatter formatter = new DataFormatter();
            List<CompletedCourseUploadRowDto> courses = new ArrayList<>();

            for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String year = readCell(row, 1, formatter);
                String semester = readCell(row, 2, formatter);
                String courseCode = readCell(row, 3, formatter);
                String courseName = readCell(row, 4, formatter);
                String category = readCell(row, 5, formatter);
                String credit = readCell(row, 8, formatter);
                String evaluationMethod = readCell(row, 9, formatter);
                String grade = readCell(row, 10, formatter);
                String gradePoint = readCell(row, 11, formatter);

                if (courseCode.isBlank() && courseName.isBlank()) {
                    continue;
                }

                courses.add(new CompletedCourseUploadRowDto(
                        year,
                        semester,
                        courseCode,
                        courseName,
                        category,
                        credit,
                        evaluationMethod,
                        grade,
                        gradePoint
                ));
            }

            return courses;
        } catch (IOException e) {
            throw new RuntimeException("엑셀 파싱 중 오류가 발생했습니다.", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 없습니다.");
        }
    }

    private String readCell(Row row, int cellIndex, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(cellIndex)).trim();
    }
}
