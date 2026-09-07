package com.oibieldev.gamedev_api.client.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics.QuotaViolation;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

class GeminiImageErrorDiagnosticsTests {

    private static final String MODEL = "gemini-3.1-flash-image";
    private static final String ENDPOINT = "https://example.test/v1/models/" + MODEL + ":generateContent";
    private static final String METRIC = "generativelanguage.googleapis.com/generate_content_paid_tier_requests";
    private static final String MINUTE_QUOTA = "GenerateRequestsPerMinutePerProjectPerModel-PaidTier";
    private static final String DAY_QUOTA = "GenerateRequestsPerDayPerProjectPerModel-PaidTier";

    private MockRestServiceServer server;
    private GeminiImageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test")
                .defaultHeader("x-goog-api-key", "synthetic-test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiImageClient(builder.build(), MODEL);
    }

    @AfterEach
    void verifyRequests() {
        // Every scenario expects exactly one request: error diagnosis must never retry generation.
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "\"0\""})
    void identifiesExplicitZeroQuotaWithoutClaimingUsageWasConsumed(String value) {
        ImageGenerationException failure = fail(429, errorWithDetails(quota(MINUTE_QUOTA, value)));

        assertThat(failure.getReason()).isEqualTo(Reason.QUOTA_UNAVAILABLE);
        assertThat(failure.getDiagnostics().httpStatus()).isEqualTo(429);
        assertThat(failure.getDiagnostics().providerStatus()).isEqualTo("RESOURCE_EXHAUSTED");
        assertThat(failure.getDiagnostics().model()).isEqualTo(MODEL);
        assertThat(failure.getDiagnostics().quotas()).containsExactly(
                new QuotaViolation(METRIC, MINUTE_QUOTA, 0L, MODEL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"quotaValue\":null"})
    void missingOrNullQuotaValueIsNotZero(String valueField) {
        String detail = """
                {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{
                  "quotaMetric":"%s","quotaId":"%s"%s
                }]}
                """.formatted(METRIC, MINUTE_QUOTA, valueField);

        ImageGenerationException failure = fail(429, errorWithDetails(detail));

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(failure.getDiagnostics().quotas()).hasSize(1);
        assertThat(failure.getDiagnostics().quotas().getFirst().limit()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "true", "\"invalid\"", "\"99999999999999999999999999\""})
    void malformedQuotaValueIsNotCoercedToZero(String value) {
        ImageGenerationException failure = fail(429, errorWithDetails(quota(MINUTE_QUOTA, value)));

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(failure.getDiagnostics().quotas()).hasSize(1);
        assertThat(failure.getDiagnostics().quotas().getFirst().limit()).isNull();
    }

    @Test
    void dailyQuotaWinsOverMinuteQuotaAndRetryHint() {
        String details = quota(MINUTE_QUOTA, "100") + "," + retry("3.25s")
                + "," + quota(DAY_QUOTA, "1000");

        ImageGenerationException failure = fail(429, errorWithDetails(details));

        assertThat(failure.getReason()).isEqualTo(Reason.QUOTA_EXHAUSTED);
        assertThat(failure.getDiagnostics().quotas()).hasSize(2);
        assertThat(failure.getDiagnostics().retryAfterSeconds()).isEqualTo(4L);
    }

    @Test
    void genericBillingAdviceDoesNotTurnQuotaErrorsIntoBillingFailures() {
        String body = """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                  "message":"You exceeded your current quota, please check your plan and billing details.",
                  "details":[%s]}}
                """.formatted(quota(DAY_QUOTA, "1000"));

        assertThat(fail(429, body).getReason()).isEqualTo(Reason.QUOTA_EXHAUSTED);
    }

    @Test
    void identifiesDepletedPrepaymentCreditsFromSpecificProviderMessage() {
        ImageGenerationException failure = fail(429, """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                  "message":"Your prepayment credits are depleted. Please add credits to continue."}}
                """);

        assertThat(failure.getReason()).isEqualTo(Reason.CREDITS_EXHAUSTED);
    }

    @Test
    void identifiesLegacyZeroQuotaMessageWhenStructuredQuotaValueIsAbsent() {
        ImageGenerationException failure = fail(429, """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                  "message":"Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-3.1-flash-image"}}
                """);

        assertThat(failure.getReason()).isEqualTo(Reason.QUOTA_UNAVAILABLE);
    }

    @Test
    void structuredPositiveQuotaIsAuthoritativeOverContradictoryLegacyMessage() {
        ImageGenerationException failure = fail(429, """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                  "message":"Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_paid_tier_requests, limit: 0, model: gemini-3.1-flash-image",
                  "details":[%s]}}
                """.formatted(quota(MINUTE_QUOTA, "100")));

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(failure.getDiagnostics().quotas().getFirst().limit()).isEqualTo(100L);
    }

    @Test
    void preservesDistinctLegacyLimitsWhenMinuteAndDayQuotasShareMetric() {
        String details = quota(MINUTE_QUOTA, "null") + "," + quota(DAY_QUOTA, "null");
        String message = "Quota exceeded for metric: " + METRIC + ", limit: 100, model: " + MODEL
                + "\\n* Quota exceeded for metric: " + METRIC + ", limit: 0, model: " + MODEL;
        ImageGenerationException failure = fail(429,
                "{\"error\":{\"message\":\"" + message + "\",\"details\":[" + details + "]}}");

        assertThat(failure.getReason()).isEqualTo(Reason.QUOTA_UNAVAILABLE);
        assertThat(failure.getDiagnostics().quotas()).containsExactly(
                new QuotaViolation(METRIC, MINUTE_QUOTA, null, MODEL),
                new QuotaViolation(METRIC, DAY_QUOTA, null, MODEL),
                new QuotaViolation(METRIC, null, 100L, null),
                new QuotaViolation(METRIC, null, 0L, null));
    }

    @Test
    void preservesZeroLegacyLimitWhenAnEarlierSentenceReportsPositiveLimit() {
        ImageGenerationException failure = fail(429, """
                {"error":{"message":"Quota exceeded for metric: %s, limit: 100\\n* Quota exceeded for metric: %s, limit: 0"}}
                """.formatted(METRIC, METRIC));

        assertThat(failure.getReason()).isEqualTo(Reason.QUOTA_UNAVAILABLE);
        assertThat(failure.getDiagnostics().quotas()).containsExactly(
                new QuotaViolation(METRIC, null, 100L, null), new QuotaViolation(METRIC, null, 0L, null));
    }

    @Test
    void zeroInAnUnrelatedMessageDoesNotMeanZeroQuota() {
        ImageGenerationException failure = fail(429, """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                  "message":"The prompt contained limit: 0 and unrelated text."}}
                """);

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
    }

    @Test
    void namesFreeTierQuotaInGuidanceWithoutEchoingProviderMessage() {
        String freeTierQuota = quota("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "0")
                .replace("paid_tier_requests", "free_tier_requests");

        ImageGenerationException failure = fail(429, errorWithDetails(freeTierQuota));

        assertThat(failure.getReason()).isEqualTo(Reason.QUOTA_UNAVAILABLE);
        assertThat(failure.getMessage()).containsIgnoringCase("gratuito");
        assertThat(failure.getMessage()).containsIgnoringCase("projeto");
    }

    @ParameterizedTest
    @MethodSource("knownErrorReasons")
    void interpretsTrustedErrorInfoBeforeStatusFallback(String reason, String domain, Reason expected) {
        String detail = """
                {"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"%s","domain":"%s"}
                """.formatted(reason, domain);

        ImageGenerationException failure = fail(403, errorWithDetails(detail));

        assertThat(failure.getReason()).isEqualTo(expected);
        assertThat(failure.getDiagnostics().providerReason()).isEqualTo(reason);
    }

    static Stream<Arguments> knownErrorReasons() {
        return Stream.of(
                Arguments.of("BILLING_DISABLED", "googleapis.com", Reason.BILLING_REQUIRED),
                Arguments.of("SERVICE_DISABLED", "googleapis.com", Reason.API_DISABLED),
                Arguments.of("API_KEY_INVALID", "googleapis.com", Reason.AUTHENTICATION_FAILED),
                Arguments.of("API_KEY_SERVICE_BLOCKED", "googleapis.com", Reason.ACCESS_DENIED),
                Arguments.of("API_KEY_HTTP_REFERRER_BLOCKED", "googleapis.com", Reason.ACCESS_DENIED),
                Arguments.of("API_KEY_IP_ADDRESS_BLOCKED", "googleapis.com", Reason.ACCESS_DENIED),
                Arguments.of("BILLING_DISABLED", "generativelanguage.googleapis.com", Reason.BILLING_REQUIRED));
    }

    @Test
    void ignoresKnownReasonFromUntrustedDomain() {
        ImageGenerationException failure = fail(429, errorWithDetails("""
                {"@type":"type.googleapis.com/google.rpc.ErrorInfo",
                  "reason":"BILLING_DISABLED","domain":"unrelated.example"}
                """));

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(failure.getDiagnostics().providerReason()).isNull();
    }

    @Test
    void roundsRetryDelayUpAndKeepsLargestProviderWaitHint() {
        DefaultResponseCreator response = withStatus(HttpStatusCode.valueOf(429))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Retry-After", "9")
                .body(errorWithDetails(retry("3.25s") + "," + retry("12.01s")));
        server.expect(requestTo(ENDPOINT)).andRespond(response);

        ImageGenerationException failure = captureFailure();

        assertThat(failure.getDiagnostics().retryAfterSeconds()).isEqualTo(13L);
    }

    @Test
    void readsRetryAfterEvenWhenErrorBodyCannotBeParsed() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatusCode.valueOf(429))
                .header("Retry-After", "7").body("upstream proxy page"));

        assertThat(captureFailure().getDiagnostics().retryAfterSeconds()).isEqualTo(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1s", "NaNs", "invalid", "999999999999999999999999999999s"})
    void ignoresInvalidOrUnrepresentableRetryDelay(String delay) {
        ImageGenerationException failure = fail(429, errorWithDetails(retry(delay)));

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(failure.getDiagnostics().retryAfterSeconds()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<html>proxy</html>", "null", "{", "{}", "[]",
            "{\"error\":\"sensitive-provider-detail\"}",
            "{\"error\":{\"details\":42}}", "{\"error\":{\"details\":[null,42,{}]}}"})
    void malformedAndUnknownBodiesPreserveSafeHttpFallback(String body) {
        ImageGenerationException failure = fail(429, body);

        assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(failure.getDiagnostics().httpStatus()).isEqualTo(429);
        assertThat(failure.getMessage()).doesNotContain("O limite de geração de imagens foi atingido.");
    }

    @Test
    void oversizedBodyDoesNotPreventSafeFallback() {
        String body = "{\"error\":{\"message\":\"" + "x".repeat(1024 * 1024) + "\"}}";

        assertThat(fail(429, body).getReason()).isEqualTo(Reason.RATE_LIMITED);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void filtersProviderTextAndUnknownMetadataFromDiagnostics(CapturedOutput output) {
        ImageGenerationException failure = fail(429, """
                {"error":{"code":429,"status":"SECRET_PROVIDER_STATUS",
                  "message":"sensitive-provider-detail synthetic-test-key private forest prompt",
                  "details":[
                    {"@type":"type.googleapis.com/google.rpc.ErrorInfo",
                     "reason":"SECRET_PROVIDER_REASON","domain":"googleapis.com",
                     "metadata":{"apiKey":"synthetic-test-key","prompt":"private forest prompt"}},
                    {"@type":"type.googleapis.com/google.rpc.DebugInfo",
                     "detail":"sensitive-provider-detail","stackEntries":["private forest prompt"]},
                    {"@type":"type.googleapis.com/google.rpc.Help",
                     "links":[{"url":"https://example.test/?key=synthetic-test-key"}]},
                    {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{
                     "quotaMetric":"generativelanguage.googleapis.com/generate_content_paid_tier_requests",
                     "quotaId":"GenerateRequestsPerMinutePerProjectPerModel-PaidTier",
                     "subject":"private forest prompt","description":"sensitive-provider-detail",
                     "quotaDimensions":{"model":"gemini-3.1-flash-image","apiKey":"synthetic-test-key"}}]}
                  ]}}
                """);

        assertThat(failure.getDiagnostics().providerStatus()).isNull();
        assertThat(failure.getDiagnostics().providerReason()).isNull();
        assertThat(failure.getDiagnostics().toString()).doesNotContain(
                "sensitive-provider-detail", "synthetic-test-key", "private forest prompt",
                "SECRET_PROVIDER_STATUS", "SECRET_PROVIDER_REASON", "https://example.test");
        assertThat(output.getAll()).contains("Image provider failure:").doesNotContain(
                "sensitive-provider-detail", "synthetic-test-key", "private forest prompt",
                "SECRET_PROVIDER_STATUS", "SECRET_PROVIDER_REASON", "https://example.test");
    }

    @Test
    void doesNotExposeArbitraryTextInQuotaIdentifiersOrModelDimensions() {
        ImageGenerationException failure = fail(429, errorWithDetails("""
                {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{
                  "quotaMetric":"private forest prompt <script>alert(1)</script>",
                  "quotaId":"sensitive-provider-detail key=synthetic-test-key",
                  "quotaDimensions":{"model":"private forest prompt <img>"}
                }]}
                """));

        assertThat(failure.getDiagnostics().toString()).doesNotContain(
                "private forest prompt", "sensitive-provider-detail", "synthetic-test-key", "<script>", "<img>");
    }

    private ImageGenerationException fail(int status, String body) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body(body));
        return captureFailure();
    }

    private ImageGenerationException captureFailure() {
        ImageGenerationException failure = assertThrows(ImageGenerationException.class,
                () -> client.generateImages("private forest prompt"));
        assertThat(failure.getDiagnostics()).isNotNull();
        assertThat(failure.getMessage()).doesNotContain(
                "sensitive-provider-detail", "synthetic-test-key", "private forest prompt", "https://example.test");
        return failure;
    }

    private static String errorWithDetails(String details) {
        return "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[" + details + "]}}";
    }

    private static String quota(String id, String value) {
        return """
                {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{
                  "quotaMetric":"%s","quotaId":"%s","quotaValue":%s,
                  "quotaDimensions":{"model":"%s","location":"global"}
                }]}
                """.formatted(METRIC, id, value, MODEL);
    }

    private static String retry(String delay) {
        return "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"" + delay + "\"}";
    }
}
