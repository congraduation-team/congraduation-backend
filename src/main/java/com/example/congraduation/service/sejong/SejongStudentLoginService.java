package com.example.congraduation.service.sejong;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import com.example.congraduation.service.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SejongStudentLoginService {

    private final SejongAuthService sejongAuthService;
    private final SejongProfileService sejongProfileService;
    private final StudentService studentService;

    public SejongStudentLoginService(
            SejongAuthService sejongAuthService,
            SejongProfileService sejongProfileService,
            StudentService studentService
    ) {
        this.sejongAuthService = sejongAuthService;
        this.sejongProfileService = sejongProfileService;
        this.studentService = studentService;
    }

    @Transactional
    public Student login(SejongLoginRequestDto loginRequestDto) {
        SejongSession session = sejongAuthService.login(loginRequestDto);
        try {
            SejongProfileResponseDto profile = sejongProfileService.fetchUserProfile(session);
            return studentService.findOrCreate(profile);
        } finally {
            session.cleanup();
        }
    }
}
