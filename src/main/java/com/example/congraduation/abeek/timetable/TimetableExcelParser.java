package com.example.congraduation.abeek.timetable;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 세종대 강의시간표 엑셀/CSV → {@link TimetableOffering} 목록.
 * 헤더명 기준으로 컬럼을 찾아, 열 순서가 조금 달라도 동작한다.
 */
@Service
public class TimetableExcelParser {

    public List<TimetableOffering> parse(MultipartFile file) {
        validateFile(file);
        String name = originalFilename(file).toLowerCase(Locale.ROOT);

        try {
            if (name.endsWith(".csv")) {
                return parseCsv(file.getInputStream());
            }
            return parseWorkbook(file.getInputStream());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("강의시간표 파싱 중 오류: " + e.getMessage(), e);
        }
    }

    private List<TimetableOffering> parseWorkbook(InputStream inputStream) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("엑셀에 시트가 없습니다.");
            }

            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("엑셀 헤더 행이 비어 있습니다.");
            }

            Map<String, Integer> columns = mapHeaderColumns(headerRow, formatter);
            requireColumns(columns);

            List<TimetableOffering> offerings = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                TimetableOffering offering = toOffering(columns, col -> readCell(row, col, formatter));
                if (offering != null) {
                    offerings.add(offering);
                }
            }
            if (offerings.isEmpty()) {
                throw new IllegalArgumentException("파싱된 개설 강좌가 없습니다. 파일/헤더를 확인하세요.");
            }
            return offerings;
        }
    }

    private List<TimetableOffering> parseCsv(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV 헤더가 비어 있습니다.");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = splitCsvLine(headerLine);
            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String key = normalizeHeader(headers.get(i));
                if (!key.isBlank()) {
                    columns.putIfAbsent(key, i);
                }
            }
            requireColumns(columns);

            List<TimetableOffering> offerings = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = splitCsvLine(line);
                TimetableOffering offering = toOffering(columns, col ->
                        col < cells.size() ? cells.get(col).trim() : "");
                if (offering != null) {
                    offerings.add(offering);
                }
            }
            if (offerings.isEmpty()) {
                throw new IllegalArgumentException("파싱된 개설 강좌가 없습니다. 파일/헤더를 확인하세요.");
            }
            return offerings;
        }
    }

    private TimetableOffering toOffering(Map<String, Integer> columns, CellReader reader) {
        String courseCode = cell(columns, reader, "학수번호");
        String courseName = cell(columns, reader, "교과목명");
        if (courseCode.isBlank() && courseName.isBlank()) {
            return null;
        }

        return new TimetableOffering(
                blankToNull(cell(columns, reader, "개설대학")),
                blankToNull(cell(columns, reader, "개설학과전공")),
                blankToNull(courseCode),
                blankToNull(cell(columns, reader, "분반")),
                blankToNull(courseName),
                blankToNull(cell(columns, reader, "이수구분")),
                blankToNull(cell(columns, reader, "학년(학기)")),
                parseCredits(cell(columns, reader, "학점")),
                blankToNull(cell(columns, reader, "수업유형")),
                blankToNull(cell(columns, reader, "요일및강의시간")),
                blankToNull(cell(columns, reader, "강의실")),
                blankToNull(cell(columns, reader, "메인교수명")),
                blankToNull(cell(columns, reader, "주관학과"))
        );
    }

    private Map<String, Integer> mapHeaderColumns(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        short last = headerRow.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String key = normalizeHeader(readCell(headerRow, i, formatter));
            if (!key.isBlank()) {
                columns.putIfAbsent(key, i);
            }
        }
        return columns;
    }

    private void requireColumns(Map<String, Integer> columns) {
        if (!columns.containsKey("학수번호") || !columns.containsKey("교과목명")) {
            throw new IllegalArgumentException(
                    "세종 강의시간표 헤더(학수번호, 교과목명)를 찾을 수 없습니다. "
                            + "공식 강의시간표 xlsx/xls/csv를 업로드하세요."
            );
        }
    }

    private static String cell(Map<String, Integer> columns, CellReader reader, String headerKey) {
        Integer index = columns.get(headerKey);
        if (index == null) {
            return "";
        }
        return reader.read(index);
    }

    private static Double parseCredits(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.replaceAll("[\\s\\n\\r]", "");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 없습니다. multipart file 파트를 확인하세요.");
        }
        String name = originalFilename(file).toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv"))) {
            throw new IllegalArgumentException("xlsx, xls, csv 파일만 지원합니다: " + name);
        }
    }

    private String originalFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null ? "" : name;
    }

    private String readCell(Row row, int cellIndex, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(cellIndex)).trim();
    }

    /** 단순 CSV 분할 (따옴표 필드 지원). */
    static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    @FunctionalInterface
    private interface CellReader {
        String read(int columnIndex);
    }
}
