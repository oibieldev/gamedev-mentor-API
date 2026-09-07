package com.oibieldev.gamedev_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.oibieldev.gamedev_api.dto.image.ImageGenerationError;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;

@RestControllerAdvice(assignableTypes = ImageGenerationController.class)
public class ImageGenerationExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ImageGenerationError> invalidRequest() {
        return ResponseEntity.badRequest().body(new ImageGenerationError(
                "INVALID_IMAGE_REQUEST",
                "Envie um JSON com prompt não vazio de até 4000 caracteres."));
    }

    @ExceptionHandler(ImageGenerationException.class)
    public ResponseEntity<ImageGenerationError> providerFailure(ImageGenerationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case BLOCKED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        String code = switch (exception.getReason()) {
            case RATE_LIMITED -> "IMAGE_PROVIDER_RATE_LIMITED";
            case UNAVAILABLE -> "IMAGE_PROVIDER_UNAVAILABLE";
            case TIMEOUT -> "IMAGE_PROVIDER_TIMEOUT";
            case INVALID_RESPONSE -> "IMAGE_PROVIDER_INVALID_RESPONSE";
            case BLOCKED -> "IMAGE_GENERATION_BLOCKED";
        };
        return ResponseEntity.status(status).body(new ImageGenerationError(code, exception.getMessage()));
    }
}
