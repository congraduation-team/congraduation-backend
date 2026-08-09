package com.example.congraduation.service.sejong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;
import org.junit.jupiter.api.Test;

class SejongReadingStatusServiceTest {

    private final SejongReadingStatusService service = new SejongReadingStatusService();

    @Test
    void parseReadingStatusParsesAreaCountsAndCompletion() {
        String html = """
                <html>
                <body>
                  <h3>영역별 인증현황</h3>
                  <table>
                    <tr><th>구분</th><th>이수권수</th><th>인증권수</th></tr>
                    <tr><td>서양의 역사와 사상 (4권)</td><td>5권</td><td>5권</td></tr>
                    <tr><td>동양의 역사와 사상 (2권)</td><td>3권</td><td>3권</td></tr>
                    <tr><td>동·서양의 문학 (3권)</td><td>3권</td><td>3권</td></tr>
                    <tr><td>과학 사상 (1권)</td><td>1권</td><td>1권</td></tr>
                    <tr><td>합계</td><td>12권</td><td>12권</td></tr>
                  </table>
                </body>
                </html>
                """;

        SejongReadingStatusResponseDto result = service.parseReadingStatus(html);

        assertTrue(result.completed());
        assertEquals("현재 고전독서인증 완료!", result.title());
        assertEquals("고전독서인증이 인증되었습니다.", result.message());
        assertEquals(4, result.areas().size());
        assertEquals("서양의 역사와 사상", result.areas().getFirst().name());
        assertEquals(5, result.areas().getFirst().completedCount());
        assertEquals(5, result.areas().getFirst().certifiedCount());
        assertEquals(4, result.areas().getFirst().requiredCount());
    }

    @Test
    void parseReadingStatusMarksIncompleteAreas() {
        String html = """
                <html>
                <body>
                  <div>
                    <span>영역별 인증현황</span>
                  </div>
                  <div>
                    <table>
                      <tr><th>구분</th><th>이수권수</th><th>인증권수</th></tr>
                      <tr><td>서양의 역사와 사상 (4권)</td><td>2권</td><td>2권</td></tr>
                      <tr><td>동양의 역사와 사상 (2권)</td><td>2권</td><td>2권</td></tr>
                    </table>
                  </div>
                </body>
                </html>
                """;

        SejongReadingStatusResponseDto result = service.parseReadingStatus(html);

        assertFalse(result.completed());
        assertEquals("현재 고전독서인증 진행 중", result.title());
        assertEquals("고전독서인증이 아직 남아 있습니다.", result.message());
        assertFalse(result.areas().getFirst().satisfied());
    }
}
