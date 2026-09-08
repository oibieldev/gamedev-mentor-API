package com.oibieldev.gamedev_api.dto.gemini.image;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiImageResponse(
    List<Candidate> candidates,
    PromptFeedback promptFeedback
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
        Content content,
        String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        List<Part> parts
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
        String text,
        InlineData inlineData,
        Boolean thought
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InlineData(
        String mimeType,
        String data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptFeedback(
        String blockReason
    ) {
    }
}
