package com.oibieldev.gamedev_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oibieldev.gamedev_api.client.ImageGenerationClient;
import com.oibieldev.gamedev_api.dto.image.GeneratedImage;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationRequest;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

class ImageGenerationServiceTests {

    private ImageGenerationClient client;
    private ImageGenerationService service;

    @BeforeEach
    void setUp() {
        client = mock(ImageGenerationClient.class);
        service = new ImageGenerationService(client);
    }

    @Test
    void stripsOuterWhitespaceAndDelegatesExactlyOnce() {
        ImageGenerationResponse expected = imageResponse();
        when(client.generateImages("Um castelo de pixel art")).thenReturn(expected);

        ImageGenerationResponse actual = service.generateImages(
                new ImageGenerationRequest(" \n\t\u2003Um castelo de pixel art\u2003 \r\n"));

        assertSame(expected, actual);
        verify(client).generateImages("Um castelo de pixel art");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequestsBeforeCallingProvider(ImageGenerationRequest request) {
        assertThrows(IllegalArgumentException.class, () -> service.generateImages(request));

        verifyNoInteractions(client);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of((ImageGenerationRequest) null),
                Arguments.of(new ImageGenerationRequest(null)),
                Arguments.of(new ImageGenerationRequest("")),
                Arguments.of(new ImageGenerationRequest(" \n\t\r\u2003")),
                Arguments.of(new ImageGenerationRequest(
                        "x".repeat(ImageGenerationService.MAX_PROMPT_LENGTH + 1))),
                Arguments.of(new ImageGenerationRequest(
                        " ".repeat(ImageGenerationService.MAX_PROMPT_LENGTH) + "x")));
    }

    @Test
    void acceptsPromptAtMaximumLength() {
        String prompt = "x".repeat(ImageGenerationService.MAX_PROMPT_LENGTH);
        ImageGenerationResponse expected = imageResponse();
        when(client.generateImages(prompt)).thenReturn(expected);

        assertSame(expected, service.generateImages(new ImageGenerationRequest(prompt)));

        verify(client).generateImages(prompt);
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("invalidProviderResponses")
    void rejectsProviderResponsesWithoutImages(ImageGenerationResponse response) {
        when(client.generateImages("Uma floresta")).thenReturn(response);

        ImageGenerationException exception = assertThrows(ImageGenerationException.class,
                () -> service.generateImages(new ImageGenerationRequest("Uma floresta")));

        assertEquals(Reason.INVALID_RESPONSE, exception.getReason());
        verify(client).generateImages("Uma floresta");
        verifyNoMoreInteractions(client);
    }

    static Stream<Arguments> invalidProviderResponses() {
        return Stream.of(
                Arguments.of((ImageGenerationResponse) null),
                Arguments.of(new ImageGenerationResponse(List.of())));
    }

    @Test
    void preservesProviderFailureWithoutRetrying() {
        ImageGenerationException expected = new ImageGenerationException(Reason.RATE_LIMITED);
        when(client.generateImages("Uma floresta")).thenThrow(expected);

        ImageGenerationException actual = assertThrows(ImageGenerationException.class,
                () -> service.generateImages(new ImageGenerationRequest("Uma floresta")));

        assertSame(expected, actual);
        verify(client).generateImages("Uma floresta");
        verifyNoMoreInteractions(client);
    }

    private static ImageGenerationResponse imageResponse() {
        return new ImageGenerationResponse(List.of(new GeneratedImage("image/png", "aW1hZ2U=")));
    }
}
