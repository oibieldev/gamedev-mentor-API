package com.oibieldev.gamedev_api.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.oibieldev.gamedev_api.client.ImageGenerationClient;
import com.oibieldev.gamedev_api.client.TextGenerationClient;
import com.oibieldev.gamedev_api.client.gemini.GeminiImageClient;
import com.oibieldev.gamedev_api.client.gemini.GeminiTextClient;
import com.oibieldev.gamedev_api.dto.image.GeneratedImage;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;

class ImageGenerationConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImageGenerationConfiguration.class)
            .withPropertyValues(
                    "gemini.api.base-url=https://example.test",
                    "gemini.api.key=synthetic-test-key"
            );

    @Test
    void selectsGeminiByDefaultWithIndependentModelAndFiniteTimeouts() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ImageGenerationClient.class);
            assertThat(context.getBean(ImageGenerationClient.class)).isInstanceOf(GeminiImageClient.class);
            assertThat(context).doesNotHaveBean(RestClient.class);
            GeminiImageProperties properties = context.getBean(GeminiImageProperties.class);
            assertThat(properties.model()).isEqualTo("gemini-3.1-flash-lite-image");
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(120));
        });
    }

    @Test
    void bindsExplicitImageConfigurationWithoutChangingTheTextClient() {
        contextRunner.withUserConfiguration(GeminiConfiguration.class, GeminiTextClient.class)
                .withPropertyValues(
                        "image.generation.provider=gemini",
                        "gemini.api.model=text-test-model",
                        "gemini.image.model=image-test-model",
                        "gemini.image.connect-timeout=2s",
                        "gemini.image.read-timeout=30s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(ImageGenerationClient.class)
                            .hasSingleBean(TextGenerationClient.class)
                            .hasSingleBean(RestClient.class);
                    assertThat(context.getBean(RestClient.class)).isSameAs(context.getBean("geminiRestClient"));
                    GeminiImageProperties properties = context.getBean(GeminiImageProperties.class);
                    assertThat(properties.model()).isEqualTo("image-test-model");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void allowsAnAlternativeProviderToSupplyTheContract() {
        ImageGenerationClient alternate = prompt -> new ImageGenerationResponse(
                List.of(new GeneratedImage("image/png", "cG5n"))
        );
        contextRunner.withBean(ImageGenerationClient.class, () -> alternate)
                .withPropertyValues("image.generation.provider=alternate")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ImageGenerationClient.class);
                    assertThat(context.getBean(ImageGenerationClient.class)).isSameAs(alternate);
                });
    }

    @Test
    void unknownProviderFailsWhenTheApplicationRequiresImageGeneration() {
        contextRunner.withUserConfiguration(RequiredClientConfiguration.class)
                .withPropertyValues("image.generation.provider=unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasStackTraceContaining(ImageGenerationClient.class.getName());
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gemini.image.model=",
            "gemini.image.connect-timeout=0s",
            "gemini.image.connect-timeout=-1s",
            "gemini.image.read-timeout=0ms",
            "gemini.image.read-timeout=not-a-duration",
            "gemini.image.read-timeout=2147483648ms"
    })
    void invalidImageSettingsFailAtStartup(String _property) {
        contextRunner.withPropertyValues(_property).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredClientConfiguration {

        @Bean
        ClientConsumer clientConsumer(ImageGenerationClient _client) {
            return new ClientConsumer(_client);
        }
    }

    record ClientConsumer(ImageGenerationClient client) {
    }
}
