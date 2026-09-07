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
import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics;
import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics.QuotaViolation;
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
    void rejectsInvalidJsonRequestsWithoutCallingProvider(String _body) throws Exception {
        mvc.perform(post("/api/images").contentType(MediaType.APPLICATION_JSON).content(_body))
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
            Reason _reason, int _statusCode, String _code) throws Exception {
        when(client.generateImages("Uma floresta")).thenThrow(new ImageGenerationException(_reason,
                new IllegalStateException("secret upstream body and API key")));

        mvc.perform(post("/api/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Uma floresta\"}"))
                .andExpect(status().is(_statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(_code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.diagnostics").doesNotExist())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(content().string(not(containsString("secret upstream"))))
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        verify(client).generateImages("Uma floresta");
        verifyNoMoreInteractions(client);
    }

    static Stream<Arguments> providerFailures() {
        return Stream.of(
                Arguments.of(Reason.RATE_LIMITED, 429, "IMAGE_PROVIDER_RATE_LIMITED"),
                Arguments.of(Reason.QUOTA_UNAVAILABLE, 429, "IMAGE_PROVIDER_QUOTA_UNAVAILABLE"),
                Arguments.of(Reason.QUOTA_EXHAUSTED, 429, "IMAGE_PROVIDER_QUOTA_EXHAUSTED"),
                Arguments.of(Reason.BILLING_REQUIRED, 503, "IMAGE_PROVIDER_BILLING_REQUIRED"),
                Arguments.of(Reason.CREDITS_EXHAUSTED, 503, "IMAGE_PROVIDER_CREDITS_EXHAUSTED"),
                Arguments.of(Reason.AUTHENTICATION_FAILED, 502, "IMAGE_PROVIDER_AUTHENTICATION_FAILED"),
                Arguments.of(Reason.ACCESS_DENIED, 502, "IMAGE_PROVIDER_ACCESS_DENIED"),
                Arguments.of(Reason.MODEL_NOT_FOUND, 502, "IMAGE_PROVIDER_MODEL_NOT_FOUND"),
                Arguments.of(Reason.API_DISABLED, 503, "IMAGE_PROVIDER_API_DISABLED"),
                Arguments.of(Reason.UNAVAILABLE, 503, "IMAGE_PROVIDER_UNAVAILABLE"),
                Arguments.of(Reason.TIMEOUT, 504, "IMAGE_PROVIDER_TIMEOUT"),
                Arguments.of(Reason.INVALID_RESPONSE, 502, "IMAGE_PROVIDER_INVALID_RESPONSE"),
                Arguments.of(Reason.BLOCKED, 422, "IMAGE_GENERATION_BLOCKED"));
    }

    @Test
    void includesStructuredDiagnosticsAndRetryHeaderForTemporaryRateLimit() throws Exception {
        ImageProviderDiagnostics diagnostics = new ImageProviderDiagnostics(
                429, "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED", "gemini-3.1-flash-image",
                List.of(new QuotaViolation(
                        "generativelanguage.googleapis.com/generate_content_paid_tier_requests",
                        "GenerateRequestsPerMinutePerProjectPerModel-PaidTier", 100L,
                        "gemini-3.1-flash-image")), 4L);
        when(client.generateImages("Uma floresta")).thenThrow(new ImageGenerationException(
                Reason.RATE_LIMITED, new IllegalStateException("secret upstream body and API key"), diagnostics));

        mvc.perform(post("/api/images").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Uma floresta\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("IMAGE_PROVIDER_RATE_LIMITED"))
                .andExpect(header().string("Retry-After", "4"))
                .andExpect(jsonPath("$.diagnostics.httpStatus").value(429))
                .andExpect(jsonPath("$.diagnostics.providerStatus").value("RESOURCE_EXHAUSTED"))
                .andExpect(jsonPath("$.diagnostics.providerReason").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.diagnostics.model").value("gemini-3.1-flash-image"))
                .andExpect(jsonPath("$.diagnostics.quotas[0].id").value(
                        "GenerateRequestsPerMinutePerProjectPerModel-PaidTier"))
                .andExpect(jsonPath("$.diagnostics.quotas[0].limit").value(100))
                .andExpect(jsonPath("$.diagnostics.retryAfterSeconds").value(4))
                .andExpect(content().string(not(containsString("secret upstream"))))
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("nonTemporaryQuotaFailures")
    void doesNotAdvertiseShortRetryAsFixForNonTemporaryQuotaFailure(Reason _reason) throws Exception {
        ImageProviderDiagnostics diagnostics = new ImageProviderDiagnostics(
                429, "RESOURCE_EXHAUSTED", null, "gemini-3.1-flash-image", List.of(), 4L);
        when(client.generateImages("Uma floresta")).thenThrow(new ImageGenerationException(_reason, null, diagnostics));

        mvc.perform(post("/api/images").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Uma floresta\"}"))
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.diagnostics.retryAfterSeconds").value(4));
    }

    static Stream<Reason> nonTemporaryQuotaFailures() {
        return Stream.of(Reason.QUOTA_UNAVAILABLE, Reason.QUOTA_EXHAUSTED,
                Reason.BILLING_REQUIRED, Reason.CREDITS_EXHAUSTED);
    }

    @ParameterizedTest
    @MethodSource("missingRetryDelays")
    void omitsRetryHeaderWithoutPositiveDelay(Long _delay) throws Exception {
        ImageProviderDiagnostics diagnostics = new ImageProviderDiagnostics(
                429, null, null, "gemini-3.1-flash-image", List.of(), _delay);
        when(client.generateImages("Uma floresta")).thenThrow(new ImageGenerationException(
                Reason.RATE_LIMITED, null, diagnostics));

        mvc.perform(post("/api/images").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Uma floresta\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    static Stream<Arguments> missingRetryDelays() {
        return Stream.of(Arguments.of((Long) null), Arguments.of(0L), Arguments.of(-1L));
    }
}
