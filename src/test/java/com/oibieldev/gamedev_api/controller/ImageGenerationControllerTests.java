package com.oibieldev.gamedev_api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.oibieldev.gamedev_api.client.ImageGenerationClient;
import com.oibieldev.gamedev_api.dto.image.GeneratedImage;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;
import com.oibieldev.gamedev_api.service.ImageGenerationService;

class ImageGenerationControllerTests {

    private ImageGenerationClient client;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        client = mock(ImageGenerationClient.class);
        mvc = MockMvcBuilders.standaloneSetup(new ImageGenerationController(new ImageGenerationService(client)))
                .setControllerAdvice(new ImageGenerationExceptionHandler())
                .build();
    }

    @Test
    void returnsProviderNeutralImagesAsJsonWithoutCaching() throws Exception {
        when(client.generateImages("Um castelo de pixel art")).thenReturn(new ImageGenerationResponse(List.of(
                new GeneratedImage("image/png", "cG5n"),
                new GeneratedImage("image/jpeg", "anBlZw=="))));

        mvc.perform(post("/api/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"  Um castelo de pixel art  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].mimeType").value("image/png"))
                .andExpect(jsonPath("$.images[0].data").value("cG5n"))
                .andExpect(jsonPath("$.images[1].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.images[1].data").value("anBlZw=="));

        verify(client).generateImages("Um castelo de pixel art");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("invalidJsonRequests")
    void rejectsInvalidJsonRequestsWithoutCallingProvider(String body) throws Exception {
        mvc.perform(post("/api/images").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(client);
    }

    static Stream<String> invalidJsonRequests() {
        return Stream.of(
                "",
                "null",
                "{}",
                "{\"prompt\":null}",
                "{\"prompt\":\"\"}",
                "{\"prompt\":\" \\n\\t \"}",
                "{\"prompt\":",
                "{\"prompt\":{\"nested\":\"value\"}}",
                "{\"prompt\":\"" + "x".repeat(ImageGenerationService.MAX_PROMPT_LENGTH + 1) + "\"}");
    }

    @Test
    void requiresJsonContentType() throws Exception {
        mvc.perform(post("/api/images").contentType(MediaType.TEXT_PLAIN).content("Um castelo"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void mapsProviderFailuresToStableErrorsWithoutLeakingUpstreamDetails(
            Reason reason, int statusCode, String code) throws Exception {
        when(client.generateImages("Uma floresta")).thenThrow(new ImageGenerationException(reason,
                new IllegalStateException("secret upstream body and API key")));

        mvc.perform(post("/api/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Uma floresta\"}"))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(content().string(not(containsString("secret upstream"))))
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        verify(client).generateImages("Uma floresta");
        verifyNoMoreInteractions(client);
    }

    static Stream<Arguments> providerFailures() {
        return Stream.of(
                Arguments.of(Reason.RATE_LIMITED, 429, "IMAGE_PROVIDER_RATE_LIMITED"),
                Arguments.of(Reason.UNAVAILABLE, 503, "IMAGE_PROVIDER_UNAVAILABLE"),
                Arguments.of(Reason.TIMEOUT, 504, "IMAGE_PROVIDER_TIMEOUT"),
                Arguments.of(Reason.INVALID_RESPONSE, 502, "IMAGE_PROVIDER_INVALID_RESPONSE"),
                Arguments.of(Reason.BLOCKED, 422, "IMAGE_GENERATION_BLOCKED"));
    }
}
