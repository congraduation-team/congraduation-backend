package com.example.congraduation.exception;

import com.example.congraduation.auth.JwtAuthenticationException;
import com.example.congraduation.auth.JwtAuthorizationException;
import com.example.congraduation.auth.LoginRateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleJwtAuthentication(JwtAuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(JwtAuthorizationException.class)
    public ResponseEntity<ApiErrorResponse> handleJwtAuthorization(JwtAuthorizationException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRateLimit(LoginRateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(new ApiErrorResponse("TOO_MANY_LOGIN_ATTEMPTS", e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "MISSING_MULTIPART_PART",
                        "[기이수 업로드] multipart 파트 누락: " + e.getRequestPartName()
                                + ". form-data 키 이름은 file 이어야 합니다."
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse(
                        "UPLOAD_TOO_LARGE",
                        "[기이수 업로드] 파일 크기가 서버 제한을 초과했습니다."
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException e) {
        // DB 원문 메시지에는 SQL·컬럼값(학번, 성적 등)이 섞일 수 있어 서버 로그에만 남긴다.
        log.error("Data access error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "DATA_ACCESS_ERROR",
                        "[DB] 데이터 처리 중 오류가 발생했습니다."
                ));
    }
}
