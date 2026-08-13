package com.example.congraduation.service.sejong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import org.junit.jupiter.api.Test;

class SejongProfileServiceTest {

    private final SejongProfileService service = new SejongProfileService();

    @Test
    void parseProfileFromHtmlReadsCompletedSemesters() {
        String html = """
                <html>
                <body>
                  <table>
                    <tbody>
                      <tr><th class="td-left" scope="row">학과명</th><td class="td-left">컴퓨터공학과</td></tr>
                      <tr><th class="td-left" scope="row">학번</th><td class="td-left">24012357</td></tr>
                      <tr><th class="td-left" scope="row">이름</th><td class="td-left">김정현</td></tr>
                      <tr><th class="td-left" scope="row">학년</th><td class="td-left">3</td></tr>
                      <tr><th class="td-left" scope="row">사용자 상태</th><td class="td-left">재학</td></tr>
                      <tr><th class="td-left" scope="row">이수 학기</th><td class="td-left">5 학기</td></tr>
                    </tbody>
                  </table>
                </body>
                </html>
                """;

        SejongProfileResponseDto profile = service.parseProfileFromHtml(html);

        assertEquals("컴퓨터공학과", profile.getMajor());
        assertEquals("24012357", profile.getStudentId());
        assertEquals("김정현", profile.getName());
        assertEquals(3, profile.getGradeLevel());
        assertEquals(5, profile.getCompletedSemesters());
    }

    @Test
    void parseProfileFromHtmlAllowsMissingCompletedSemesters() {
        String html = """
                <html>
                <body>
                  <table>
                    <tr><th>학과명</th><td>경영학부</td></tr>
                    <tr><th>학번</th><td>22000001</td></tr>
                    <tr><th>이름</th><td>테스트</td></tr>
                    <tr><th>학년</th><td>4</td></tr>
                  </table>
                </body>
                </html>
                """;

        SejongProfileResponseDto profile = service.parseProfileFromHtml(html);

        assertEquals(4, profile.getGradeLevel());
        assertNull(profile.getCompletedSemesters());
    }
}
