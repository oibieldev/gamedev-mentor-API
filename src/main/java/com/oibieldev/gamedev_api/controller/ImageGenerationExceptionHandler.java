package com.oibieldev.gamedev_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.oibieldev.gamedev_api.dto.image.ImageGenerationError;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

@RestControllerAdvice(assignableTypes = ImageGenerationController.class)
public class ImageGenerationExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ImageGenerationError> invalidRequest() {
        return ResponseEntity.badRequest().body(new ImageGenerationError(
                "INVALID_IMAGE_REQUEST",
                "Envie um JSON com prompt não vazio de até 4000 caracteres."));
    }

    @ExceptionHandler(ImageGenerationException.class)
    public ResponseEntity<ImageGenerationError> providerFailure(ImageGenerationException _exception) {
        HttpStatus status = switch (_exception.getReason()) {
            case RATE_LIMITED, QUOTA_UNAVAILABLE, QUOTA_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE, BILLING_REQUIRED, CREDITS_EXHAUSTED, API_DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case AUTHENTICATION_FAILED, ACCESS_DENIED, MODEL_NOT_FOUND -> HttpStatus.BAD_GATEWAY;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case BLOCKED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        String code = switch (_exception.getReason()) {
            case RATE_LIMITED -> "IMAGE_PROVIDER_RATE_LIMITED";
            case QUOTA_UNAVAILABLE -> "IMAGE_PROVIDER_QUOTA_UNAVAILABLE";
            case QUOTA_EXHAUSTED -> "IMAGE_PROVIDER_QUOTA_EXHAUSTED";
            case BILLING_REQUIRED -> "IMAGE_PROVIDER_BILLING_REQUIRED";
            case CREDITS_EXHAUSTED -> "IMAGE_PROVIDER_CREDITS_EXHAUSTED";
            case AUTHENTICATION_FAILED -> "IMAGE_PROVIDER_AUTHENTICATION_FAILED";
            case ACCESS_DENIED -> "IMAGE_PROVIDER_ACCESS_DENIED";
            case MODEL_NOT_FOUND -> "IMAGE_PROVIDER_MODEL_NOT_FOUND";
            case API_DISABLED -> "IMAGE_PROVIDER_API_DISABLED";
            case UNAVAILABLE -> "IMAGE_PROVIDER_UNAVAILABLE";
            case TIMEOUT -> "IMAGE_PROVIDER_TIMEOUT";
            case INVALID_RESPONSE -> "IMAGE_PROVIDER_INVALID_RESPONSE";
            case BLOCKED -> "IMAGE_GENERATION_BLOCKED";
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (_exception.getReason() == Reason.RATE_LIMITED && _exception.getDiagnostics() != null) {
            Long retryAfter = _exception.getDiagnostics().retryAfterSeconds();
            if (retryAfter != null && retryAfter > 0) {
                response.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            }
        }
        return response.body(new ImageGenerationError(code, _exception.getMessage(), _exception.getDiagnostics()));
    }
}
