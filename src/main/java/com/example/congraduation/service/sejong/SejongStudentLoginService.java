package com.example.congraduation.service.sejong;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;
import com.example.congraduation.service.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SejongStudentLoginService {

    private final SejongAuthService sejongAuthService;
    private final SejongProfileService sejongProfileService;
    private final SejongReadingStatusService sejongReadingStatusService;
    private final StudentService studentService;

    public SejongStudentLoginService(
            SejongAuthService sejongAuthService,
            SejongProfileService sejongProfileService,
            SejongReadingStatusService sejongReadingStatusService,
            StudentService studentService
    ) {
        this.sejongAuthService = sejongAuthService;
        this.sejongProfileService = sejongProfileService;
        this.sejongReadingStatusService = sejongReadingStatusService;
        this.studentService = studentService;
    }

    @Transactional
    public SejongStudentLoginResult login(SejongLoginRequestDto loginRequestDto) {
        SejongSession session = sejongAuthService.login(loginRequestDto);
        try {
            String html = sejongProfileService.fetchReadingStatusPageHtml(session);
            SejongProfileResponseDto profile = sejongProfileService.parseProfileFromHtml(html);
            SejongReadingStatusResponseDto readingStatus = sejongReadingStatusService.parseReadingStatus(html);
            Student student = studentService.findOrCreate(profile);
            return new SejongStudentLoginResult(student, readingStatus);
        } finally {
            session.cleanup();
        }
    }
}
