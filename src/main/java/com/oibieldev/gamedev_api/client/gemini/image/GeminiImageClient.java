package com.oibieldev.gamedev_api.client.gemini.image;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.oibieldev.gamedev_api.client.ImageGenerationClient;
import com.oibieldev.gamedev_api.dto.gemini.image.GeminiImageRequest;
import com.oibieldev.gamedev_api.dto.gemini.image.GeminiImageResponse;
import com.oibieldev.gamedev_api.dto.image.GeneratedImage;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

public class GeminiImageClient implements ImageGenerationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiImageClient.class);
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );
    private static final Set<String> BLOCKED_FINISH_REASONS = Set.of(
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION", "ESCALATION"
    );

    private final RestClient restClient;
    private final String model;

    public GeminiImageClient(RestClient _restClient, String _model) {
        Assert.notNull(_restClient, "The Gemini image HTTP client is required.");
        Assert.hasText(_model, "The Gemini image model is required.");
        this.restClient = _restClient;
        this.model = _model;
    }

    @Override
    public ImageGenerationResponse generateImages(String _prompt) {
        GeminiImageResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/models/{model}:generateContent", model)
                    .body(GeminiImageRequest.fromPrompt(_prompt))
                    .retrieve()
                    .body(GeminiImageResponse.class);
        } catch (RestClientResponseException exception) {
            ImageGenerationException failure = GeminiImageErrorMapper.map(exception, model);
            // Log only filtered facts, never the upstream body, request prompt or exception cause.
            LOGGER.warn("Image provider failure: reason={}, diagnostics={}", failure.getReason(), failure.getDiagnostics());
            throw failure;
        } catch (ResourceAccessException exception) {
            throw new ImageGenerationException(
                    this.isTimeout(exception) ? Reason.TIMEOUT : Reason.UNAVAILABLE, exception
            );
        } catch (RestClientException exception) {
            // Timeouts can also occur while the response body is being decoded.
            throw new ImageGenerationException(
                    this.isTimeout(exception) ? Reason.TIMEOUT : Reason.INVALID_RESPONSE, exception
            );
        }
        return this.extractImages(response);
    }

    private ImageGenerationResponse extractImages(GeminiImageResponse _response) {
        if (_response == null) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }
        if (_response.promptFeedback() != null) {
            String blockReason = _response.promptFeedback().blockReason();
            if (blockReason != null && !blockReason.isBlank()
                    && !"BLOCK_REASON_UNSPECIFIED".equals(blockReason)) {
                throw new ImageGenerationException(Reason.BLOCKED);
            }
        }
        if (_response.candidates() == null || _response.candidates().isEmpty()
                || _response.candidates().getFirst() == null) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }

        GeminiImageResponse.Candidate candidate = _response.candidates().getFirst();
        if (candidate.finishReason() != null && BLOCKED_FINISH_REASONS.contains(candidate.finishReason())) {
            throw new ImageGenerationException(Reason.BLOCKED);
        }
        if (!"STOP".equals(candidate.finishReason()) || candidate.content() == null
                || candidate.content().parts() == null) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }

        List<GeneratedImage> images = new ArrayList<>();
        for (GeminiImageResponse.Part part : candidate.content().parts()) {
            if (part == null) {
                throw new ImageGenerationException(Reason.INVALID_RESPONSE);
            }
            if (Boolean.TRUE.equals(part.thought()) || part.inlineData() == null) {
                continue;
            }
            GeminiImageResponse.InlineData data = part.inlineData();
            if (data.mimeType() == null || !SUPPORTED_MIME_TYPES.contains(data.mimeType())
                    || data.data() == null || data.data().isBlank()) {
                throw new ImageGenerationException(Reason.INVALID_RESPONSE);
            }
            try {
                if (Base64.getDecoder().decode(data.data()).length == 0) {
                    throw new ImageGenerationException(Reason.INVALID_RESPONSE);
                }
            } catch (IllegalArgumentException exception) {
                throw new ImageGenerationException(Reason.INVALID_RESPONSE, exception);
            }
            images.add(new GeneratedImage(data.mimeType(), data.data()));
        }
        if (images.isEmpty()) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }
        return new ImageGenerationResponse(images);
    }

    private boolean isTimeout(Throwable _exception) {
        for (Throwable cause = _exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
