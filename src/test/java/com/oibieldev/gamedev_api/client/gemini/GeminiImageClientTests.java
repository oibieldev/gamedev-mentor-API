package com.oibieldev.gamedev_api.client.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.oibieldev.gamedev_api.dto.image.GeneratedImage;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

class GeminiImageClientTests {

    private static final String ENDPOINT = "https://example.test/v1/models/test-image-model:generateContent";
    private static final String IMAGE_PART = """
            {"inlineData":{"mimeType":"image/png","data":"cG5n"}}
            """;

    private MockRestServiceServer server;
    private GeminiImageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://example.test")
                .defaultHeader("x-goog-api-key", "synthetic-test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiImageClient(builder.build(), "test-image-model");
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void sendsImageModalitiesAndExtractsAllFinalImagesInOrder() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "synthetic-test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "contents":[{"parts":[{"text":"A pixel art forest"}]}],
                          "generationConfig":{"responseModalities":["TEXT","IMAGE"]}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "modelVersion":"provider-metadata",
                          "candidates":[{
                            "finishReason":"STOP",
                            "index":0,
                            "content":{"role":"model","parts":[
                              {"text":"An explanation"},
                              {"thought":true,"inlineData":{"mimeType":"image/png","data":"invalid"}},
                              {"inlineData":{"mimeType":"image/png","data":"cG5n"}},
                              {"text":"Another view"},
                              {"inlineData":{"mimeType":"image/jpeg","data":"anBlZw=="}},
                              {"thought":false,"inlineData":{"mimeType":"image/webp","data":"d2VicA=="}}
                            ]}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generateImages("A pixel art forest").images()).containsExactly(
                new GeneratedImage("image/png", "cG5n"),
                new GeneratedImage("image/jpeg", "anBlZw=="),
                new GeneratedImage("image/webp", "d2VicA==")
        );
    }

    @Test
    void onlyUsesTheFirstCandidate() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("""
                {"candidates":[
                  {"finishReason":"STOP","content":{"parts":[%s]}},
                  {"finishReason":"SAFETY"}
                ]}
                """.formatted(IMAGE_PART), MediaType.APPLICATION_JSON));

        assertThat(client.generateImages("forest").images())
                .containsExactly(new GeneratedImage("image/png", "cG5n"));
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsMalformedAndMissingImages(String _response) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(_response, MediaType.APPLICATION_JSON));

        this.assertFailure(Reason.INVALID_RESPONSE);
    }

    static Stream<String> invalidResponses() {
        return Stream.of(
                "", "null", "{", "{}", "{\"candidates\":null}", "{\"candidates\":[]}",
                "{\"candidates\":[null]}", "{\"candidates\":[{}]}",
                "{\"candidates\":[{\"finishReason\":\"STOP\"}]}",
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{}}]}",
                responseWithParts("[]"), responseWithParts("[null]"),
                responseWithParts("[{\"text\":\"I cannot generate an image.\"}]"),
                responseWithParts("[{\"thought\":true,\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"cG5n\"}}]"),
                responseWithParts("[{\"inlineData\":{}}]"),
                responseWithParts("[{\"inlineData\":{\"mimeType\":\"image/png\"}}]"),
                responseWithParts("[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"\"}}]"),
                responseWithParts("[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"   \"}}]"),
                responseWithParts("[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"not-base64!\"}}]"),
                responseWithParts("[{\"inlineData\":{\"mimeType\":\"image/svg+xml\",\"data\":\"c3Zn\"}}]"),
                responseWithParts("[{\"inlineData\":{\"mimeType\":\"text/html\",\"data\":\"aHRtbA==\"}}]"),
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[]}},"
                        + "{\"finishReason\":\"STOP\",\"content\":{\"parts\":[" + IMAGE_PART + "]}}]}"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAX_TOKENS", "NO_IMAGE", "OTHER", "FINISH_REASON_UNSPECIFIED", ""})
    void doesNotReturnIncompleteCandidates(String _finishReason) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("""
                {"candidates":[{"finishReason":"%s","content":{"parts":[%s]}}]}
                """.formatted(_finishReason, IMAGE_PART), MediaType.APPLICATION_JSON));

        this.assertFailure(Reason.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "IMAGE_SAFETY", "OTHER"})
    void mapsPromptBlocks(String _blockReason) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("""
                {"promptFeedback":{"blockReason":"%s"}}
                """.formatted(_blockReason), MediaType.APPLICATION_JSON));

        this.assertFailure(Reason.BLOCKED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION", "ESCALATION"
    })
    void neverReturnsImagesFromBlockedCandidates(String _finishReason) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("""
                {"candidates":[{"finishReason":"%s","content":{"parts":[%s]}}]}
                """.formatted(_finishReason, IMAGE_PART), MediaType.APPLICATION_JSON));

        this.assertFailure(Reason.BLOCKED);
    }

    @Test
    void unspecifiedPromptFeedbackDoesNotBlockAValidImage() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("""
                {
                  "promptFeedback":{"blockReason":"BLOCK_REASON_UNSPECIFIED"},
                  "candidates":[{"finishReason":"STOP","content":{"parts":[%s]}}]
                }
                """.formatted(IMAGE_PART), MediaType.APPLICATION_JSON));

        assertThat(client.generateImages("forest").images()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 408, 429, 500, 503, 504})
    void mapsHttpFailuresWithoutRetryingOrExposingProviderDetails(int _status) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatusCode.valueOf(_status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"sensitive-provider-detail\"}"));

        this.assertFailure(switch (_status) {
            case 401 -> Reason.AUTHENTICATION_FAILED;
            case 403 -> Reason.ACCESS_DENIED;
            case 404 -> Reason.MODEL_NOT_FOUND;
            case 429 -> Reason.RATE_LIMITED;
            default -> Reason.UNAVAILABLE;
        });
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void mapsConnectionAndReadTimeoutsWithoutRetrying(IOException _timeout) {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(_timeout));

        this.assertFailure(Reason.TIMEOUT);
    }

    static Stream<IOException> timeouts() {
        return Stream.of(
                new SocketTimeoutException("sensitive-provider-detail"),
                new HttpTimeoutException("sensitive-provider-detail"),
                new IOException("wrapper", new SocketTimeoutException("sensitive-provider-detail"))
        );
    }

    @Test
    void mapsNetworkErrorsToUnavailable() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new IOException("sensitive-provider-detail")));

        this.assertFailure(Reason.UNAVAILABLE);
    }

    @Test
    void mapsUnsupportedResponseContentTypeToInvalidResponse() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("upstream proxy page", MediaType.TEXT_HTML));

        this.assertFailure(Reason.INVALID_RESPONSE);
    }

    private static String responseWithParts(String _parts) {
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":" + _parts + "}}]}";
    }

    private void assertFailure(Reason _expected) {
        assertThatThrownBy(() -> client.generateImages("forest"))
                .isInstanceOfSatisfying(ImageGenerationException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(_expected);
                    assertThat(exception.getMessage()).doesNotContain(
                            "sensitive-provider-detail", "synthetic-test-key", "https://example.test"
                    );
                });
    }
}
