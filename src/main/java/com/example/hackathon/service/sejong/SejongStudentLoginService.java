package com.example.hackathon.service.sejong;

import com.example.hackathon.domain.Student;
import com.example.hackathon.dto.sejong.SejongLoginRequestDto;
import com.example.hackathon.dto.sejong.SejongProfileResponseDto;
import com.example.hackathon.service.student.StudentService;
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
        String ssoToken = sejongAuthService.getSsoToken(loginRequestDto);
        SejongProfileResponseDto profile = sejongProfileService.fetchUserProfile(ssoToken);
        return studentService.findOrCreate(profile);
    }
}
