package com.oibieldev.gamedev_api.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

@ConfigurationProperties("gemini.image")
public record GeminiImageProperties(
        @DefaultValue("gemini-3.1-flash-image") String model,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("120s") Duration readTimeout
) {

    public GeminiImageProperties {
        Assert.hasText(model, "gemini.image.model must not be blank.");
        validateTimeout(connectTimeout, "gemini.image.connect-timeout");
        validateTimeout(readTimeout, "gemini.image.read-timeout");
    }

    private static void validateTimeout(Duration timeout, String property) {
        Assert.notNull(timeout, property + " is required.");
        Assert.isTrue(timeout.compareTo(Duration.ofMillis(1)) >= 0
                        && timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0,
                property + " must be between 1ms and " + Integer.MAX_VALUE + "ms.");
    }
}
