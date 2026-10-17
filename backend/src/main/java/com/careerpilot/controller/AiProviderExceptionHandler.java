package com.careerpilot.controller;
import com.careerpilot.service.ConfigurableCareerAiService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AiProviderExceptionHandler {
    @ExceptionHandler(ConfigurableCareerAiService.AiProviderException.class)
    public ResponseEntity<Map<String, String>> handle(ConfigurableCareerAiService.AiProviderException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("detail", exception.getMessage()));
    }
}
