package com.oibieldev.gamedev_api.service.generation;

import org.springframework.stereotype.Service;

import com.oibieldev.gamedev_api.client.ImageGenerationClient;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationRequest;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    public static final int MAX_PROMPT_LENGTH = 4000;

    private final ImageGenerationClient imageClient;

    public ImageGenerationResponse generateImages(ImageGenerationRequest _request) {
        if (_request == null || _request.prompt() == null || _request.prompt().isBlank()) {
            throw new IllegalArgumentException("Informe uma descrição para gerar a imagem.");
        }
        if (_request.prompt().length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("A descrição deve ter no máximo 4000 caracteres.");
        }

        ImageGenerationResponse response = imageClient.generateImages(_request.prompt().strip());
        if (response == null || response.images().isEmpty()) {
            throw new ImageGenerationException(Reason.INVALID_RESPONSE);
        }
        return response;
    }
}
