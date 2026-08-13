package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SejongProfileService {

    private static final Logger log = LoggerFactory.getLogger(SejongProfileService.class);
    private static final int MAX_PROFILE_ATTEMPTS = 3;

    private static final String CLASSIC_INDEX_URL =
            "https://classic.sejong.ac.kr/classic/index.do";
    private static final String READING_STATUS_URL =
            "https://classic.sejong.ac.kr/classic/reading/status.do";
    private static final String ENGLISH_CERTIFICATION_URL =
            "https://classic.sejong.ac.kr/classic/english/certification-status.do";

    public SejongProfileResponseDto fetchUserProfile(SejongSession session) {
        return parseProfileFromHtml(fetchReadingStatusPageHtml(session));
    }

    public String fetchReadingStatusPageHtml(SejongSession session) {
        return fetchClassicPageHtml(session, READING_STATUS_URL, "세종 프로필 조회");
    }

    public String fetchEnglishCertificationPageHtml(SejongSession session) {
        return fetchClassicPageHtml(session, ENGLISH_CERTIFICATION_URL, "세종 영어인증 조회");
    }

    private String fetchClassicPageHtml(SejongSession session, String targetUrl, String description) {
        if (session == null) {
            throw new IllegalArgumentException("세종 로그인 세션이 비어 있습니다.");
        }

        List<String> bootstrapCommand = SejongCurlSupport.baseCurlCommand(session.cookieJarPath());
        bootstrapCommand.add("--header");
        bootstrapCommand.add("Referer: https://portal.sejong.ac.kr/");
        bootstrapCommand.add("--header");
        bootstrapCommand.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        bootstrapCommand.add("--header");
        bootstrapCommand.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        bootstrapCommand.add(CLASSIC_INDEX_URL);
        SejongCurlSupport.executeDiscardBody(bootstrapCommand, "세종 classic 인덱스 세션 준비");

        String html = requestPageHtml(session, targetUrl, CLASSIC_INDEX_URL, MAX_PROFILE_ATTEMPTS, description);
        if (html.contains("로그인") || html.contains("세종대학교 포털")) {
            throw new IllegalStateException("SSO 인증 실패: 로그인 페이지가 반환되었습니다.");
        }

        return html;
    }

    private String requestPageHtml(
            SejongSession session,
            String targetUrl,
            String referer,
            int remainingAttempts,
            String description
    ) {
        if (remainingAttempts <= 0) {
            throw new IllegalStateException("세종 프로필 조회 리다이렉트 한도를 초과했습니다.");
        }

        Path headerFilePath;
        try {
            headerFilePath = Files.createTempFile("sejong-profile-headers-", ".txt");
        } catch (IOException e) {
            throw new RuntimeException("세종 프로필 조회 헤더 파일 생성에 실패했습니다.", e);
        }

        List<String> command = buildPageCommand(session, targetUrl, referer);
        SejongCurlSupport.CurlExchange exchange = SejongCurlSupport.executeWithHeaders(
                command,
                headerFilePath,
                description
        );

        log.info(
                "Sejong profile response status={}, location={}, remainingAttempts={}",
                exchange.statusCode(),
                exchange.location(),
                remainingAttempts
        );

        String redirectUrl = SejongCurlSupport.resolveRedirectUrl(targetUrl, exchange.location());
        if (redirectUrl == null || redirectUrl.isBlank()) {
            if (exchange.statusCode() >= 400) {
                throw new IllegalStateException("세종 프로필 조회 실패: HTTP " + exchange.statusCode());
            }
            return exchange.body();
        }

        if (redirectUrl.contains("/_custom/sejong/sso/login.jsp")) {
            List<String> gatewayCommand = SejongCurlSupport.baseCurlCommand(session.cookieJarPath());
            gatewayCommand.add("--location");
            gatewayCommand.add("--header");
            gatewayCommand.add("Referer: " + CLASSIC_INDEX_URL);
            gatewayCommand.add("--header");
            gatewayCommand.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            gatewayCommand.add("--header");
            gatewayCommand.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            gatewayCommand.add(redirectUrl);
            SejongCurlSupport.executeDiscardBody(gatewayCommand, description + " SSO 게이트 통과");

            return requestPageHtml(session, targetUrl, CLASSIC_INDEX_URL, remainingAttempts - 1, description);
        }

        throw new IllegalStateException("예상하지 못한 세종 프로필 리다이렉트가 발생했습니다: " + redirectUrl);
    }

    private List<String> buildPageCommand(SejongSession session, String targetUrl, String referer) {
        List<String> command = SejongCurlSupport.baseCurlCommand(session.cookieJarPath());
        command.add("--header");
        command.add("Referer: " + referer);
        command.add("--header");
        command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        command.add("--header");
        command.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        command.add(targetUrl);
        return command;
    }

    public SejongProfileResponseDto parseProfileFromHtml(String html) {
        Document document = Jsoup.parse(html);

        String major = document.select("th:containsOwn(학과명) + td").text().trim();
        String studentId = document.select("th:containsOwn(학번) + td").text().trim();
        String name = document.select("th:containsOwn(이름) + td").text().trim();
        String gradeLevel = document.select("th:containsOwn(학년) + td").text().trim();
        String completedSemesters = document.select("th:containsOwn(이수 학기) + td").text().trim();

        if (major.isBlank() || studentId.isBlank() || name.isBlank() || gradeLevel.isBlank()) {
            throw new IllegalStateException("세종 프로필 파싱 중 필수 데이터가 누락되었습니다.");
        }

        return new SejongProfileResponseDto(major, studentId, name, gradeLevel, completedSemesters);
    }
}
