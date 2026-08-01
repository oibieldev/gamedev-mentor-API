package com.oibieldev.gamedev_api.dto.gemini;

import java.util.List;

public record GeminiResponse(
    List<GeminiCandidate> candidates
) {
} 
