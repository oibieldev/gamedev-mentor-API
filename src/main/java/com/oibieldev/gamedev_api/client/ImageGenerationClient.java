package com.oibieldev.gamedev_api.client;

import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;

/**
 * Provider boundary for text-to-image generation. Implementations return final
 * raster images as Base64 and translate upstream failures to ImageGenerationException.
 */
public interface ImageGenerationClient {

    ImageGenerationResponse generateImages(String _prompt);
}
