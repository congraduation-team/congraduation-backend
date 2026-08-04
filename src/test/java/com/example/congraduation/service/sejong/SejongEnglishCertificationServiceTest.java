package com.example.congraduation.service.sejong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.dto.sejong.SejongEnglishCertificationResponseDto;
import org.junit.jupiter.api.Test;

class SejongEnglishCertificationServiceTest {

    private final SejongEnglishCertificationService service = new SejongEnglishCertificationService();

    @Test
    void parseCertificationStatusReturnsNotSubmittedWhenListIsEmpty() {
        String html = """
                <html>
                <body>
                  <div class="co-board">
                    <div class="bn-list-common type01">
                      <div class="table-wrap"></div>
                    </div>
                  </div>
                </body>
                </html>
                """;

        SejongEnglishCertificationResponseDto result = service.parseCertificationStatus(html);

        assertFalse(result.submitted());
        assertFalse(result.certified());
        assertEquals("NOT_SUBMITTED", result.status());
    }

    @Test
    void parseCertificationStatusIgnoresEmptyTemplateBox() {
        String html = """
                <html>
                <body>
                  <div class="b-title-box">
                    <a class="b-title js-popup-open" data-popup-type="english-register-popup" href="javascript:void(0);"></a>
                    <div class="b-m-con">
                      <span class="b-major"></span>
                      <span class="b-student-id"></span>
                      <span class="b-exam"></span>
                      <span class="b-score"></span>
                      <span class="b-submit-date"></span>
                      <span class="b-status"></span>
                    </div>
                  </div>
                </body>
                </html>
                """;

        SejongEnglishCertificationResponseDto result = service.parseCertificationStatus(html);

        assertFalse(result.submitted());
        assertFalse(result.certified());
        assertEquals("NOT_SUBMITTED", result.status());
    }

    @Test
    void parseCertificationStatusParsesApprovedSubmission() {
        String html = """
                <html>
                <body>
                  <div class="b-title-box">
                    <a class="b-title">홍길동</a>
                    <div class="b-m-con">
                      <span class="b-major">컴퓨터공학과</span>
                      <span class="b-student-id">21012345</span>
                      <span class="b-exam">TOEIC</span>
                      <span class="b-score">850</span>
                      <span class="b-submit-date">2026-07-31</span>
                      <span class="b-status">인증완료</span>
                    </div>
                  </div>
                </body>
                </html>
                """;

        SejongEnglishCertificationResponseDto result = service.parseCertificationStatus(html);

        assertTrue(result.submitted());
        assertTrue(result.certified());
        assertEquals("TOEIC", result.examType());
        assertEquals("850", result.score());
        assertEquals("2026-07-31", result.submitDate());
        assertEquals("인증완료", result.status());
    }
}
