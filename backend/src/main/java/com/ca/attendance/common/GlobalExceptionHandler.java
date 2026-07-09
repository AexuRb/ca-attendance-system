package com.ca.attendance.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(body(ex.status(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception ex) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "请求参数不完整或格式错误"));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("Unhandled API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"));
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );
    }
}
