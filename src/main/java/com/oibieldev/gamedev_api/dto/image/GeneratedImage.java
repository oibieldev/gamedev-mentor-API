package com.oibieldev.gamedev_api.dto.image;

/** A raster image with its MIME type and raw Base64 data (without a data-URL prefix). */
public record GeneratedImage(
    String mimeType,
    String data
) {
}
