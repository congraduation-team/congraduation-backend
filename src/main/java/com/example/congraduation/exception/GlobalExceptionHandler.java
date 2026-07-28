package com.example.congraduation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TranscriptNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTranscriptNotFound(TranscriptNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("TRANSCRIPT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DepartmentPolicyNotConfiguredException.class)
    public ResponseEntity<ApiErrorResponse> handleDepartmentPolicyNotConfigured(DepartmentPolicyNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse("DEPARTMENT_POLICY_NOT_CONFIGURED", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("SEJONG_INTEGRATION_FAILED", e.getMessage()));
    }
}
