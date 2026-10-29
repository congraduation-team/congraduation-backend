package com.example.congraduation.service.sejong;

import com.example.congraduation.auth.JwtService;
import com.example.congraduation.auth.LoginRateLimiter;
import com.example.congraduation.auth.JwtService.JwtTokenDto;
import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.sejong.SejongEnglishCertificationResponseDto;
import com.example.congraduation.dto.sejong.SejongLoginRequestDto;
import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.congraduation.service.student.StudentService;
import com.example.congraduation.service.stats.SiteStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SejongStudentLoginService {

    private static final Logger log = LoggerFactory.getLogger(SejongStudentLoginService.class);
    private static final int MAX_USER_ID_LENGTH = 20;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final SejongAuthService sejongAuthService;
    private final SejongProfileService sejongProfileService;
    private final SejongReadingStatusService sejongReadingStatusService;
    private final SejongEnglishCertificationService sejongEnglishCertificationService;
    private final StudentService studentService;
    private final JwtService jwtService;
    private final SiteStatsService siteStatsService;
    private final LoginRateLimiter loginRateLimiter;

    public SejongStudentLoginService(
            SejongAuthService sejongAuthService,
            SejongProfileService sejongProfileService,
            SejongReadingStatusService sejongReadingStatusService,
            SejongEnglishCertificationService sejongEnglishCertificationService,
            StudentService studentService,
            JwtService jwtService,
            SiteStatsService siteStatsService,
            LoginRateLimiter loginRateLimiter
    ) {
        this.sejongAuthService = sejongAuthService;
        this.sejongProfileService = sejongProfileService;
        this.sejongReadingStatusService = sejongReadingStatusService;
        this.sejongEnglishCertificationService = sejongEnglishCertificationService;
        this.studentService = studentService;
        this.jwtService = jwtService;
        this.siteStatsService = siteStatsService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public SejongStudentLoginResult login(SejongLoginRequestDto loginRequestDto, String clientIp) {
        validate(loginRequestDto);
        String userId = loginRequestDto.getUserId();
        loginRateLimiter.acquire(clientIp, userId);

        SejongSession session;
        try {
            session = sejongAuthService.login(loginRequestDto);
        } catch (SejongInvalidCredentialsException e) {
            throw e;
        } catch (RuntimeException e) {
            loginRateLimiter.recordInconclusive(clientIp, userId);
            throw e;
        }
        loginRateLimiter.recordSuccess(clientIp, userId);

        try {
            String html = sejongProfileService.fetchReadingStatusPageHtml(session);
            SejongProfileResponseDto profile = sejongProfileService.parseProfileFromHtml(html);
            SejongReadingStatusResponseDto readingStatus = sejongReadingStatusService.parseReadingStatus(html);
            Student student = studentService.findOrCreate(profile);
            studentService.updateClassicReadingCertification(student, readingStatus);
            SejongEnglishCertificationResponseDto englishCertification = fetchEnglishCertification(session);
            studentService.updateEnglishCertification(student, englishCertification);
            siteStatsService.recordStudentVisit(student.getId());
            JwtTokenDto jwtToken = jwtService.issueToken(student);
            return new SejongStudentLoginResult(student, readingStatus, englishCertification, jwtToken);
        } finally {
            session.cleanup();
        }
    }

    private void validate(SejongLoginRequestDto loginRequestDto) {
        if (loginRequestDto == null
                || isBlank(loginRequestDto.getUserId())
                || isBlank(loginRequestDto.getPassword())) {
            throw new IllegalArgumentException("학번과 비밀번호를 입력해 주세요.");
        }
        if (loginRequestDto.getUserId().length() > MAX_USER_ID_LENGTH
                || loginRequestDto.getPassword().length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("학번 또는 비밀번호 형식이 올바르지 않습니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
