package com.oibieldev.gamedev_api.dto.gemini;

import java.util.List;

public record GeminiRequest(
    List<GeminiContent> contents
) {

}
