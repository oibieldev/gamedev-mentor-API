package com.oibieldev.gamedev_api.dto.image;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageGenerationError(
    String code,
    String message,
    ImageProviderDiagnostics diagnostics
) {

    public ImageGenerationError(String _code, String _message) {
        this(_code, _message, null);
    }
}
