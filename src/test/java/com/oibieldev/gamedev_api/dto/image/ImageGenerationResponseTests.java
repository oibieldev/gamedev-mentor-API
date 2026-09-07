package com.oibieldev.gamedev_api.dto.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ImageGenerationResponseTests {

    @Test
    void keepsSnapshotOfProviderImagesAndPreventsExternalMutation() {
        GeneratedImage image = new GeneratedImage("image/png", "aW1hZ2U=");
        List<GeneratedImage> providerImages = new ArrayList<>(List.of(image));
        ImageGenerationResponse response = new ImageGenerationResponse(providerImages);

        providerImages.clear();

        assertEquals(List.of(image), response.images());
        assertThrows(UnsupportedOperationException.class, () -> response.images().clear());
    }
}
