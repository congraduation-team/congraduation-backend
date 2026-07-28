package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class SejongProfileService {

    private static final String PROFILE_URL =
            "https://classic.sejong.ac.kr/classic/reading/status.do";

    public SejongProfileResponseDto fetchUserProfile(SejongSession session) {
        try {
            if (session == null) {
                throw new IllegalArgumentException("세종 로그인 세션이 비어 있습니다.");
            }

            HttpGet request = new HttpGet(PROFILE_URL);
            request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            request.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            request.setHeader("Referer", "https://portal.sejong.ac.kr/");
            request.setHeader("Upgrade-Insecure-Requests", "1");
            request.setHeader("User-Agent", SejongAuthService.DEFAULT_USER_AGENT);

            try (CloseableHttpResponse response = session.httpClient().execute(request)) {
                String html = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity());

                if (html.contains("로그인") || html.contains("세종대학교 포털")) {
                    throw new IllegalStateException("SSO 인증 실패: 로그인 페이지가 반환되었습니다.");
                }

                return parseProfileFromHtml(html);
            }
        } catch (IOException | ParseException e) {
            throw new RuntimeException("사용자 프로필 조회 중 오류가 발생했습니다.", e);
        }
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
