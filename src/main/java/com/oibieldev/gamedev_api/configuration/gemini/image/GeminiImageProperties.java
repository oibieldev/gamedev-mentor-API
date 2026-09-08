package com.oibieldev.gamedev_api.configuration.gemini.image;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

@ConfigurationProperties("gemini.image")
public record GeminiImageProperties(
    @DefaultValue("gemini-3.1-flash-lite-image") String model,
    @DefaultValue("10s") Duration connectTimeout,
    @DefaultValue("120s") Duration readTimeout
) {

    public GeminiImageProperties {
        Assert.hasText(model, "gemini.image.model must not be blank.");
        validateTimeout(connectTimeout, "gemini.image.connect-timeout");
        validateTimeout(readTimeout, "gemini.image.read-timeout");
    }

    private static void validateTimeout(Duration _timeout, String _property) {
        Assert.notNull(_timeout, _property + " is required.");
        Assert.isTrue(_timeout.compareTo(Duration.ofMillis(1)) >= 0
                        && _timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0,
                _property + " must be between 1ms and " + Integer.MAX_VALUE + "ms.");
    }
}
