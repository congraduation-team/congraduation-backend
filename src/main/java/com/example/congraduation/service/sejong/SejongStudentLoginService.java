package com.example.congraduation.service.sejong;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.sejong.SejongEnglishCertificationResponseDto;
import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.congraduation.service.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SejongStudentLoginService {

    private static final Logger log = LoggerFactory.getLogger(SejongStudentLoginService.class);

    private final SejongAuthService sejongAuthService;
    private final SejongProfileService sejongProfileService;
    private final SejongReadingStatusService sejongReadingStatusService;
    private final SejongEnglishCertificationService sejongEnglishCertificationService;
    private final StudentService studentService;

    public SejongStudentLoginService(
            SejongAuthService sejongAuthService,
            SejongProfileService sejongProfileService,
            SejongReadingStatusService sejongReadingStatusService,
            SejongEnglishCertificationService sejongEnglishCertificationService,
            StudentService studentService
    ) {
        this.sejongAuthService = sejongAuthService;
        this.sejongProfileService = sejongProfileService;
        this.sejongReadingStatusService = sejongReadingStatusService;
        this.sejongEnglishCertificationService = sejongEnglishCertificationService;
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
            studentService.updateClassicReadingCertification(student, readingStatus);
            SejongEnglishCertificationResponseDto englishCertification = fetchEnglishCertification(session);
            studentService.updateEnglishCertification(student, englishCertification);
            return new SejongStudentLoginResult(student, readingStatus, englishCertification);
        } finally {
            session.cleanup();
        }
    }

    private SejongEnglishCertificationResponseDto fetchEnglishCertification(SejongSession session) {
        try {
            String englishHtml = sejongProfileService.fetchEnglishCertificationPageHtml(session);
            return sejongEnglishCertificationService.parseCertificationStatus(englishHtml);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch Sejong english certification status: {}", e.getMessage());
            return SejongEnglishCertificationResponseDto.unavailable();
        }
    }
}
