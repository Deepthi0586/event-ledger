package com.eventledger.gateway.api;

import com.eventledger.gateway.service.AccountServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAccountServiceUnavailable(
            AccountServiceUnavailableException exception) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}