package com.example.congraduation.auth;

public record AuthenticatedStudent(
        Long studentId,
        String studentNo,
        boolean admin
) {
}
