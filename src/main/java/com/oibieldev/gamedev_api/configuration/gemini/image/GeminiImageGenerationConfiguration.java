package com.oibieldev.gamedev_api.configuration.gemini.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.oibieldev.gamedev_api.client.ImageGenerationClient;
import com.oibieldev.gamedev_api.client.gemini.image.GeminiImageClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeminiImageProperties.class)
public class GeminiImageGenerationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "image.generation.provider", havingValue = "gemini", matchIfMissing = true)
    public ImageGenerationClient geminiImageClient(
            @Value("${gemini.api.base-url}") String _baseUrl,
            @Value("${gemini.api.key}") String _apiKey,
            GeminiImageProperties _properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(_properties.connectTimeout());
        requestFactory.setReadTimeout(_properties.readTimeout());
        RestClient client = RestClient.builder()
                .baseUrl(_baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", _apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new GeminiImageClient(client, _properties.model());
    }
}
