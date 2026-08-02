package com.example.congraduation.abeek.service;

import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableExcelParser;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import com.example.congraduation.dto.admin.AdminUploadResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ClassScheduleAdminService {

    private final TimetableExcelParser excelParser;
    private final TimetableCatalog timetableCatalog;
    private final ObjectMapper objectMapper;
    private final TimetableGitHubSyncService gitHubSyncService;
    private final Path dataDir;
    private final Path resourcesDir;

    public ClassScheduleAdminService(
            TimetableExcelParser excelParser,
            TimetableCatalog timetableCatalog,
            ObjectMapper objectMapper,
            TimetableGitHubSyncService gitHubSyncService,
            @Value("${app.timetable.data-dir:./data/timetable-data}") String dataDir,
            @Value("${app.timetable.resources-dir:./src/main/resources/timetable-data}") String resourcesDir
    ) {
        this.excelParser = excelParser;
        this.timetableCatalog = timetableCatalog;
        this.objectMapper = objectMapper;
        this.gitHubSyncService = gitHubSyncService;
        this.dataDir = Path.of(dataDir);
        this.resourcesDir = Path.of(resourcesDir);
    }

    public AdminUploadResponseDto upload(MultipartFile file, int year, int semester) {
        validateTerm(year, semester);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 없습니다. multipart file 파트를 확인하세요.");
        }

        List<TimetableOffering> offerings = parseOfferings(file);
        TimetableTermData termData = new TimetableTermData(year, semester, offerings);

        String fileName = year + "-" + semester + ".json";
        byte[] jsonBytes = toPrettyJsonBytes(termData);

        persist(fileName, jsonBytes);
        timetableCatalog.replaceTerm(termData);

        TimetableGitHubSyncService.SyncResult github = gitHubSyncService.upsertTimetableFile(fileName, jsonBytes);

        String message = String.format(
                "%d-%d 강의시간표가 업데이트되었습니다. (%d개 개설 강좌). %s",
                year, semester, offerings.size(), github.detail()
        );
        if (github.commitUrl() != null) {
            message = message + " " + github.commitUrl();
        }
        log.info(message);
        return new AdminUploadResponseDto(
                message,
                offerings.size(),
                year,
                semester,
                github.synced(),
                github.commitUrl()
        );
    }

    private byte[] toPrettyJsonBytes(TimetableTermData termData) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(termData)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("강의시간표 JSON 직렬화 실패: " + e.getMessage(), e);
        }
    }

    private List<TimetableOffering> parseOfferings(MultipartFile file) {
        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);

        if (name.endsWith(".json")) {
            try {
                TimetableTermData parsed = objectMapper.readValue(file.getInputStream(), TimetableTermData.class);
                if (parsed.offerings() == null || parsed.offerings().isEmpty()) {
                    throw new IllegalArgumentException("JSON에 offerings가 비어 있습니다.");
                }
                return parsed.offerings();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("강의시간표 JSON 파싱 실패: " + e.getMessage(), e);
            }
        }

        if (!(name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv"))) {
            throw new IllegalArgumentException("xlsx, xls, csv, json 파일만 지원합니다: " + name);
        }

        return excelParser.parse(file);
    }

    private void persist(String fileName, byte[] jsonBytes) {
        writeBytes(dataDir, fileName, jsonBytes, true);

        Path resolvedResources = resolveResourcesDir();
        if (resolvedResources != null) {
            writeBytes(resolvedResources, fileName, jsonBytes, true);
        } else {
            log.warn("timetable resources-dir not found ({}). Wrote only to {}",
                    resourcesDir.toAbsolutePath(), dataDir.toAbsolutePath());
        }
    }

    private Path resolveResourcesDir() {
        if (Files.isDirectory(resourcesDir)) {
            return resourcesDir;
        }
        if (resourcesDir.getParent() != null && Files.isDirectory(resourcesDir.getParent())) {
            return resourcesDir;
        }
        Path cursor = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && cursor != null; i++) {
            Path candidate = cursor.resolve("src/main/resources/timetable-data");
            if (Files.isDirectory(candidate)
                    || (candidate.getParent() != null && Files.isDirectory(candidate.getParent()))) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private void writeBytes(Path dir, String fileName, byte[] jsonBytes, boolean required) {
        try {
            if (!required && !Files.isDirectory(dir)
                    && (dir.getParent() == null || !Files.isDirectory(dir.getParent()))) {
                log.debug("Skip timetable write; dir missing: {}", dir.toAbsolutePath());
                return;
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.write(target, jsonBytes);
            log.info("Saved timetable to {}", target.toAbsolutePath());
        } catch (IOException e) {
            if (required) {
                throw new IllegalArgumentException("강의시간표 파일 저장 실패: " + e.getMessage(), e);
            }
            log.warn("Failed to update timetable at {}: {}", dir.toAbsolutePath(), e.getMessage());
        }
    }

    private static void validateTerm(int year, int semester) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("year 값이 올바르지 않습니다: " + year);
        }
        if (semester < 1 || semester > 4) {
            throw new IllegalArgumentException("semester는 1~4만 가능합니다. (1/2학기, 3=여름계절, 4=겨울계절)");
        }
    }
}
