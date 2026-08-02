package com.example.congraduation.controller;

import com.example.congraduation.abeek.service.ClassScheduleAdminService;
import com.example.congraduation.dto.admin.AdminUploadResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Class Schedules", description = "관리자 강의시간표 업로드 API")
public class AdminClassScheduleController {

    private final ClassScheduleAdminService classScheduleAdminService;

    @PostMapping(value = "/class-schedules", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "학기별 강의시간표 업로드",
            description = "세종대 공식 강의시간표(.xlsx/.xls/.csv) 또는 timetable JSON을 업로드하면 "
                    + "해당 연도·학기 데이터를 교체하고 메모리 카탈로그를 즉시 반영합니다. "
                    + "파일은 app.timetable.data-dir 아래에 {year}-{semester}.json 으로 저장됩니다."
    )
    public AdminUploadResponseDto uploadClassSchedule(
            @Parameter(description = "강의시간표 파일 (.xlsx / .xls / .csv / .json)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "연도", example = "2026", required = true)
            @RequestParam int year,
            @Parameter(description = "학기 (1=1학기, 2=2학기, 3=여름계절, 4=겨울계절)", example = "1", required = true)
            @RequestParam int semester
    ) {
        return classScheduleAdminService.upload(file, year, semester);
    }
}
