package com.oibieldev.gamedev_api.dto.gemini.image;

import java.util.List;

public record GeminiImageRequest(
    List<Content> contents,
    GenerationConfig generationConfig
) {

    public static GeminiImageRequest fromPrompt(String _prompt) {
        return new GeminiImageRequest(
                List.of(
                        new Content(
                                List.of(new Part(_prompt))
                        )
                ),
                new GenerationConfig(
                        List.of("IMAGE"),
                        new ResponseFormat(
                                new ImageResponseFormat("IMAGE_JPEG")
                        )
                )
        );
    }

    public record Content(
        List<Part> parts
    ) { }

    public record Part(
        String text
    ) { }

    public record GenerationConfig(
        List<String> responseModalities,
        ResponseFormat responseFormat
    ) { }

    public record ResponseFormat(
        ImageResponseFormat image
    ) { }

    public record ImageResponseFormat(
        String mimeType
    ) { }
}
