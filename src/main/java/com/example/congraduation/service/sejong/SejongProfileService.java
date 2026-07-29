package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class SejongProfileService {

    private static final String CLASSIC_INDEX_URL =
            "https://classic.sejong.ac.kr/classic/index.do";
    private static final String PROFILE_URL =
            "https://classic.sejong.ac.kr/classic/reading/status.do";

    public SejongProfileResponseDto fetchUserProfile(SejongSession session) {
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

        String html = requestProfileHtml(session, CLASSIC_INDEX_URL);
        if (html.contains("로그인") || html.contains("세종대학교 포털")) {
            throw new IllegalStateException("SSO 인증 실패: 로그인 페이지가 반환되었습니다.");
        }

        return parseProfileFromHtml(html);
    }

    private String requestProfileHtml(SejongSession session, String referer) {
        Path headerFilePath;
        try {
            headerFilePath = Files.createTempFile("sejong-profile-headers-", ".txt");
        } catch (IOException e) {
            throw new RuntimeException("세종 프로필 조회 헤더 파일 생성에 실패했습니다.", e);
        }

        List<String> command = buildProfileCommand(session, referer);
        SejongCurlSupport.CurlExchange exchange = SejongCurlSupport.executeWithHeaders(
                command,
                headerFilePath,
                "세종 프로필 조회"
        );

        String redirectUrl = SejongCurlSupport.resolveRedirectUrl(PROFILE_URL, exchange.location());
        if (redirectUrl == null || redirectUrl.isBlank()) {
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
            SejongCurlSupport.executeDiscardBody(gatewayCommand, "세종 프로필 조회 SSO 게이트 통과");

            return SejongCurlSupport.execute(buildProfileCommand(session, CLASSIC_INDEX_URL), "세종 프로필 재조회");
        }

        return exchange.body();
    }

    private List<String> buildProfileCommand(SejongSession session, String referer) {
        List<String> command = SejongCurlSupport.baseCurlCommand(session.cookieJarPath());
        command.add("--header");
        command.add("Referer: " + referer);
        command.add("--header");
        command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        command.add("--header");
        command.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        command.add(PROFILE_URL);
        return command;
    }

    private SejongProfileResponseDto parseProfileFromHtml(String html) {
        Document document = Jsoup.parse(html);

        String major = document.select("th:contains(학과명) + td").text().trim();
        String studentId = document.select("th:contains(학번) + td").text().trim();
        String name = document.select("th:contains(이름) + td").text().trim();
        String gradeLevel = document.select("th:contains(학년) + td").text().trim();

        if (major.isBlank() || studentId.isBlank() || name.isBlank() || gradeLevel.isBlank()) {
            throw new IllegalStateException("세종 프로필 파싱 중 필수 데이터가 누락되었습니다.");
        }

        return new SejongProfileResponseDto(major, studentId, name, gradeLevel);
    }
}
