package com.oibieldev.gamedev_api.dto.gemini.image;

import java.util.List;

public record GeminiImageRequest(List<Content> contents, GenerationConfig generationConfig) {

    public static GeminiImageRequest fromPrompt(String prompt) {
        return new GeminiImageRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(List.of("TEXT", "IMAGE"))
        );
    }

    public record Content(List<Part> parts) {
    }

    public record Part(String text) {
    }

    public record GenerationConfig(List<String> responseModalities) {
    }
}
