package com.example.congraduation.controller;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.auth.StudentLoginResponseDto;
import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import com.example.congraduation.service.sejong.SejongStudentLoginService;
import com.example.congraduation.service.sejong.SejongStudentLoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "세종대학교 로그인 및 학생 정보 조회 API")
public class AuthController {

    private final SejongStudentLoginService sejongStudentLoginService;

    public AuthController(SejongStudentLoginService sejongStudentLoginService) {
        this.sejongStudentLoginService = sejongStudentLoginService;
    }

    @PostMapping("/login")
    @Operation(summary = "세종대학교 로그인", description = "학번과 비밀번호로 로그인한 뒤 학생 기본 정보를 반환합니다.")
    public ResponseEntity<StudentLoginResponseDto> login(
            @RequestBody SejongLoginRequestDto loginRequestDto
    ) {
        SejongStudentLoginResult loginResult = sejongStudentLoginService.login(loginRequestDto);
        Student student = loginResult.student();
        return ResponseEntity.ok(StudentLoginResponseDto.from(
                student,
                loginResult.readingStatus(),
                loginResult.englishCertification()
        ));
    }
}
