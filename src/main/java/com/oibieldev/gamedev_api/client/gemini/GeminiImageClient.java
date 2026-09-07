package com.oibieldev.gamedev_api.client.gemini;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;

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

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );
    private static final Set<String> BLOCKED_FINISH_REASONS = Set.of(
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION", "ESCALATION"
    );

    private final RestClient restClient;
    private final String model;

    public GeminiImageClient(RestClient restClient, String model) {
        Assert.notNull(restClient, "The Gemini image HTTP client is required.");
        Assert.hasText(model, "The Gemini image model is required.");
        this.restClient = restClient;
        this.model = model;
    }

    @Override
    public ImageGenerationResponse generateImages(String prompt) {
        GeminiImageResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/models/{model}:generateContent", model)
                    .body(GeminiImageRequest.fromPrompt(prompt))
                    .retrieve()
                    .body(GeminiImageResponse.class);
        } catch (RestClientResponseException exception) {
            Reason reason = exception.getStatusCode().value() == 429
                    ? Reason.RATE_LIMITED : Reason.UNAVAILABLE;
            throw new ImageGenerationException(reason, exception);
        } catch (ResourceAccessException exception) {
            throw new ImageGenerationException(
                    isTimeout(exception) ? Reason.TIMEOUT : Reason.UNAVAILABLE, exception
            );
        } catch (RestClientException exception) {
            // Timeouts can also occur while the response body is being decoded.
            throw new ImageGenerationException(
                    isTimeout(exception) ? Reason.TIMEOUT : Reason.INVALID_RESPONSE, exception
            );
        }
        return extractImages(response);
    }

    private ImageGenerationResponse extractImages(GeminiImageResponse response) {
        if (response == null) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }
        if (response.promptFeedback() != null) {
            String blockReason = response.promptFeedback().blockReason();
            if (blockReason != null && !blockReason.isBlank()
                    && !"BLOCK_REASON_UNSPECIFIED".equals(blockReason)) {
                throw new ImageGenerationException(Reason.BLOCKED);
            }
        }
        if (response.candidates() == null || response.candidates().isEmpty()
                || response.candidates().getFirst() == null) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }

        GeminiImageResponse.Candidate candidate = response.candidates().getFirst();
        if (candidate.finishReason() != null && BLOCKED_FINISH_REASONS.contains(candidate.finishReason())) {
            throw new ImageGenerationException(Reason.BLOCKED);
        }
        if (!"STOP".equals(candidate.finishReason()) || candidate.content() == null
                || candidate.content().parts() == null) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }

        var images = new ArrayList<GeneratedImage>();
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

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
