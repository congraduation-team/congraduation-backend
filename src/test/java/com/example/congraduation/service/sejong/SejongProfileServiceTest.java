package com.example.congraduation.service.sejong;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.congraduation.dto.sejong.SejongProfileResponseDto;
import org.junit.jupiter.api.Test;

class SejongProfileServiceTest {

    private final SejongProfileService service = new SejongProfileService();

    @Test
    void parseProfileFromHtmlIncludesCompletedSemesterCount() {
        String html = """
                <html>
                <body>
                  <table>
                    <tr><th>학과명</th><td>컴퓨터공학과</td></tr>
                    <tr><th>학번</th><td>24012357</td></tr>
                    <tr><th>이름</th><td>김정현</td></tr>
                    <tr><th>학년</th><td>3</td></tr>
                    <tr><th>이수 학기</th><td>5학기</td></tr>
                  </table>
                </body>
                </html>
                """;

        SejongProfileResponseDto result = service.parseProfileFromHtml(html);

        assertEquals("컴퓨터공학과", result.getMajor());
        assertEquals("24012357", result.getStudentId());
        assertEquals("김정현", result.getName());
        assertEquals(3, result.getGradeLevel());
        assertEquals(5, result.getCompletedSemesterCount());
    }
}
