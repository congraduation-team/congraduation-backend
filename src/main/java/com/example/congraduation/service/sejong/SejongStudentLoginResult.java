package com.example.congraduation.service.sejong;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;

public record SejongStudentLoginResult(
        Student student,
        SejongReadingStatusResponseDto readingStatus
) {
}
