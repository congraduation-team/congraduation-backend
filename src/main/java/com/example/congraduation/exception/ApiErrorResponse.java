package com.example.congraduation.exception;

public record ApiErrorResponse(
        String code,
        String message
) {
}
