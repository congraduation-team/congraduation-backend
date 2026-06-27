package com.example.hackathon.exception;

public record ApiErrorResponse(
        String code,
        String message
) {
}
