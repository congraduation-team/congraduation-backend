package com.example.congraduation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    void masksStudentIdExceptAdmissionYearPrefix() {
        assertThat(LogMasking.maskStudentId("21012345")).isEqualTo("2101****");
        assertThat(LogMasking.maskStudentId("123")).isEqualTo("****");
        assertThat(LogMasking.maskStudentId(null)).isEqualTo("(empty)");
    }

    @Test
    void redactsQueryStringAndFragment() {
        assertThat(LogMasking.redactUrl("https://classic.sejong.ac.kr/sso?ssotoken=abc&x=1"))
                .isEqualTo("https://classic.sejong.ac.kr/sso?[redacted]");
        assertThat(LogMasking.redactUrl("https://a.com/p#token=abc"))
                .isEqualTo("https://a.com/p?[redacted]");
        assertThat(LogMasking.redactUrl("https://a.com/p")).isEqualTo("https://a.com/p");
        assertThat(LogMasking.redactUrl(null)).isNull();
    }

    @Test
    void loginRequestToStringHidesPassword() {
        var dto = new com.example.congraduation.dto.sejong.SejongLoginRequestDto("21012345", "s3cret!");

        assertThat(dto.toString())
                .doesNotContain("s3cret!")
                .doesNotContain("21012345")
                .contains("2101****");
    }
}
