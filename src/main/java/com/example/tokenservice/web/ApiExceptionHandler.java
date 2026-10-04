package com.example.tokenservice.web;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 統一錯誤回應格式：{@code {"error": "<code>", "message": "<說明>"}}。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> authenticationFailed(AuthenticationException ex) {
        // 帳號不存在與密碼錯誤一律回相同訊息，避免洩漏帳號是否存在
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("invalid_credentials", "帳號或密碼錯誤"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", "請求主體必須是有效的 JSON"));
    }

    public record ErrorResponse(String error, String message) {
    }
}
