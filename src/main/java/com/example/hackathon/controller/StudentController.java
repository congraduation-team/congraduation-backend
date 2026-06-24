package com.example.hackathon.controller;

import com.example.hackathon.domain.Student;
import com.example.hackathon.dto.auth.StudentLoginResponseDto;
import com.example.hackathon.dto.student.MajorOptionDto;
import com.example.hackathon.dto.student.StudentMajorTrackUpdateRequestDto;
import com.example.hackathon.service.student.MajorCatalogService;
import com.example.hackathon.service.student.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Student", description = "학생 전공 정보 관리 API")
public class StudentController {

    private final StudentService studentService;
    private final MajorCatalogService majorCatalogService;

    public StudentController(StudentService studentService, MajorCatalogService majorCatalogService) {
        this.studentService = studentService;
        this.majorCatalogService = majorCatalogService;
    }

    @GetMapping("/major-options")
    @Operation(summary = "복수전공 선택지 조회", description = "프론트에서 복수전공 학과 선택지로 사용할 학과 목록을 반환합니다.")
    public ResponseEntity<List<MajorOptionDto>> getMajorOptions() {
        return ResponseEntity.ok(majorCatalogService.getMajorOptions());
    }

    @PatchMapping("/{studentId}/major-track")
    @Operation(summary = "학생 전공 정보 수정", description = "단일전공/복수전공 여부와 복수전공 학과를 수정합니다.")
    public ResponseEntity<StudentLoginResponseDto> updateMajorTrack(
            @PathVariable Long studentId,
            @RequestBody StudentMajorTrackUpdateRequestDto request
    ) {
        Student student = studentService.updateMajorTrack(studentId, request);
        return ResponseEntity.ok(StudentLoginResponseDto.from(student));
    }
}
