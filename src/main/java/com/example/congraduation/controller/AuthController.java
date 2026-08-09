package com.example.congraduation.controller;

import com.example.congraduation.auth.AuthRequestAttributes;
import com.example.congraduation.auth.AuthenticatedStudent;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.auth.StudentLoginResponseDto;
import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import com.example.congraduation.service.sejong.SejongStudentLoginService;
import com.example.congraduation.service.sejong.SejongStudentLoginResult;
import com.example.congraduation.service.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "세종대학교 로그인 및 학생 정보 조회 API")
public class AuthController {

    private final SejongStudentLoginService sejongStudentLoginService;
    private final StudentService studentService;

    public AuthController(SejongStudentLoginService sejongStudentLoginService, StudentService studentService) {
        this.sejongStudentLoginService = sejongStudentLoginService;
        this.studentService = studentService;
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
                loginResult.englishCertification(),
                loginResult.jwtToken()
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "현재 로그인 사용자 조회", description = "Authorization Bearer JWT 기준 현재 로그인한 학생 정보를 반환합니다.")
    public ResponseEntity<StudentLoginResponseDto> me(HttpServletRequest request) {
        AuthenticatedStudent authenticatedStudent = (AuthenticatedStudent) request.getAttribute(
                AuthRequestAttributes.AUTHENTICATED_STUDENT
        );
        Student student = studentService.getStudent(authenticatedStudent.studentId());
        return ResponseEntity.ok(StudentLoginResponseDto.from(student));
    }
}
