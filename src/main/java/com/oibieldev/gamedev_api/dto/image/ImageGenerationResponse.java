package com.oibieldev.gamedev_api.dto.image;

import java.util.List;

public record ImageGenerationResponse(
    List<GeneratedImage> images
) {

    public ImageGenerationResponse {
        images = List.copyOf(images);
    }
}
