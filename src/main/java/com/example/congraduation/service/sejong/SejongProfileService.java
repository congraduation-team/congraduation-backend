package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class SejongProfileService {

    private static final String PROFILE_BOOTSTRAP_URL =
            "https://classic.sejong.ac.kr/_custom/sejong/sso/login.jsp?auty=LOGIN&referer=%2Fclassic%2Freading%2Fstatus.do%3Fauty%3D2";
    private static final String PROFILE_URL =
            "https://classic.sejong.ac.kr/classic/reading/status.do?auty=2";

    public SejongProfileResponseDto fetchUserProfile(SejongSession session) {
        if (session == null) {
            throw new IllegalArgumentException("세종 로그인 세션이 비어 있습니다.");
        }

        List<String> bootstrapCommand = SejongCurlSupport.baseCurlCommand(session.cookieJarPath());
        bootstrapCommand.add("--location");
        bootstrapCommand.add("--header");
        bootstrapCommand.add("Referer: https://classic.sejong.ac.kr/classic/index.do");
        bootstrapCommand.add("--header");
        bootstrapCommand.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        bootstrapCommand.add("--header");
        bootstrapCommand.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        bootstrapCommand.add(PROFILE_BOOTSTRAP_URL);
        SejongCurlSupport.execute(bootstrapCommand, "세종 프로필 조회 사전 SSO 진입");

        List<String> command = SejongCurlSupport.baseCurlCommand(session.cookieJarPath());
        command.add("--header");
        command.add("Referer: " + PROFILE_BOOTSTRAP_URL);
        command.add("--header");
        command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        command.add("--header");
        command.add("Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        command.add(PROFILE_URL);

        String html = SejongCurlSupport.execute(command, "세종 프로필 조회");
        if (html.contains("로그인") || html.contains("세종대학교 포털")) {
            throw new IllegalStateException("SSO 인증 실패: 로그인 페이지가 반환되었습니다.");
        }

        return parseProfileFromHtml(html);
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
