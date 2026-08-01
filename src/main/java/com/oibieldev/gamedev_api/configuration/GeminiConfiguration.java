package com.oibieldev.gamedev_api.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;


@Configuration
public class GeminiConfiguration {

    @Bean
    public RestClient geminiRestClient(
            @Value("${gemini.api.base-url}") String _baseUrl,
            @Value("${gemini.api.key}") String _key
    ) {
        System.out.println("API-KEY: " + _key);

        return RestClient.builder()
                .baseUrl(_baseUrl)
                .defaultHeader("x-goog-api-key", _key)
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    
    }
}