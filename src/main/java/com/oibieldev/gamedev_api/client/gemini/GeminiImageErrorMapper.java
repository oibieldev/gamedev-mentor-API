package com.oibieldev.gamedev_api.client.gemini;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics;
import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics.QuotaViolation;
import com.oibieldev.gamedev_api.exception.ImageGenerationException;
import com.oibieldev.gamedev_api.exception.ImageGenerationException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Interprets the generateContent Google RPC error envelope, not the Interactions API format. */
final class GeminiImageErrorMapper {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_QUOTAS = 20;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> STATUSES = Set.of(
            "CANCELLED", "UNKNOWN", "INVALID_ARGUMENT", "DEADLINE_EXCEEDED", "NOT_FOUND",
            "ALREADY_EXISTS", "PERMISSION_DENIED", "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION",
            "ABORTED", "OUT_OF_RANGE", "UNIMPLEMENTED", "INTERNAL", "UNAVAILABLE", "DATA_LOSS",
            "UNAUTHENTICATED"
    );
    private static final List<String> ERROR_REASONS = List.of(
            "BILLING_DISABLED", "SERVICE_DISABLED", "API_KEY_INVALID",
            "API_KEY_SERVICE_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED",
            "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_IOS_APP_BLOCKED",
            "RESOURCE_QUOTA_EXCEEDED", "RATE_LIMIT_EXCEEDED"
    );
    private static final Pattern MODEL = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern METRIC = Pattern.compile("generativelanguage\\.googleapis\\.com/[A-Za-z0-9_/.-]{1,160}");
    private static final Pattern QUOTA_ID = Pattern.compile("(?:Generate|Images|Tokens|Requests)[A-Za-z0-9_-]{1,170}");
    // Older responses omit quotaValue and report the enforced value only in this sentence.
    private static final Pattern MESSAGE_QUOTA = Pattern.compile(
            "(?im)^\\s*\\*?\\s*Quota exceeded for metric:\\s*"
                    + "(generativelanguage\\.googleapis\\.com/[A-Za-z0-9_/.-]+),\\s*limit:\\s*(\\d+)"
                    + "(?=\\s*(?:,|$))");

    private GeminiImageErrorMapper() {
    }

    static ImageGenerationException map(RestClientResponseException _exception, String _model) {
        int httpStatus = _exception.getStatusCode().value();
        JsonNode error = readError(_exception);
        String providerStatus = text(error.path("status"));
        if (!STATUSES.contains(providerStatus == null ? "" : providerStatus)) providerStatus = null;
        String providerReason = null;
        List<QuotaViolation> quotas = new ArrayList<>();
        Long retryAfter = retryHeader(_exception.getResponseHeaders());
        JsonNode details = error.path("details");
        if (details.isArray()) {
            for (JsonNode detail : details) {
                String type = text(detail.path("@type"));
                if ("type.googleapis.com/google.rpc.ErrorInfo".equals(type)) {
                    String domain = text(detail.path("domain"));
                    String reason = text(detail.path("reason"));
                    if (("googleapis.com".equals(domain) || "generativelanguage.googleapis.com".equals(domain))
                            && reason != null && ERROR_REASONS.contains(reason)
                            && (providerReason == null || ERROR_REASONS.indexOf(reason) < ERROR_REASONS.indexOf(providerReason))) {
                        providerReason = reason;
                    }
                } else if ("type.googleapis.com/google.rpc.QuotaFailure".equals(type)) {
                    JsonNode violations = detail.path("violations");
                    if (violations.isArray()) {
                        for (JsonNode violation : violations) {
                            if (quotas.size() >= MAX_QUOTAS) break;
                            String metric = safe(text(violation.path("quotaMetric")), METRIC);
                            String id = safe(text(violation.path("quotaId")), QUOTA_ID);
                            String quotaModel = safe(text(violation.path("quotaDimensions").path("model")), MODEL);
                            Long limit = nonnegativeLong(violation.path("quotaValue"));
                            if (metric != null || id != null || limit != null) {
                                quotas.add(new QuotaViolation(metric, id, limit, quotaModel));
                            }
                        }
                    }
                } else if ("type.googleapis.com/google.rpc.RetryInfo".equals(type)) {
                    retryAfter = maximum(retryAfter, retryDuration(text(detail.path("retryDelay"))));
                }
            }
        }

        String message = text(error.path("message"));
        if (httpStatus == 429 && message != null) mergeMessageQuotas(message, quotas);
        Reason reason = classify(httpStatus, providerReason, message, quotas);
        ImageProviderDiagnostics diagnostics = new ImageProviderDiagnostics(
                httpStatus, providerStatus, providerReason, safe(_model, MODEL), quotas, retryAfter);
        return new ImageGenerationException(reason, _exception, diagnostics);
    }

    private static JsonNode readError(RestClientResponseException _exception) {
        if (_exception.getResponseBodyAsByteArray().length <= MAX_BODY_BYTES) {
            try {
                JsonNode root = JSON.readTree(_exception.getResponseBodyAsString());
                if (root != null && root.path("error").isObject()) return root.path("error");
            } catch (JacksonException | IllegalArgumentException ignored) {
                // Missing, malformed or non-JSON upstream bodies must retain a usable HTTP fallback.
            }
        }
        return JSON.createObjectNode();
    }

    private static Reason classify(int _status, String _providerReason, String _message, List<QuotaViolation> _quotas) {
        if ("BILLING_DISABLED".equals(_providerReason)) return Reason.BILLING_REQUIRED;
        if ("SERVICE_DISABLED".equals(_providerReason)) return Reason.API_DISABLED;
        if ("API_KEY_INVALID".equals(_providerReason) || _status == 401) return Reason.AUTHENTICATION_FAILED;
        if (_providerReason != null && _providerReason.startsWith("API_KEY_") && _providerReason.endsWith("_BLOCKED")) {
            return Reason.ACCESS_DENIED;
        }
        if (_status == 403) return Reason.ACCESS_DENIED;
        if (_status == 404) return Reason.MODEL_NOT_FOUND;
        if (_status != 429) return Reason.UNAVAILABLE;

        // This narrow textual fallback is needed when billing failures have no ErrorInfo.
        String normalized = _message == null ? "" : _message.stripLeading().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("your prepayment credits are depleted")
                || normalized.startsWith("prepayment credits are depleted")) {
            return Reason.CREDITS_EXHAUSTED;
        }
        if (_quotas.stream().anyMatch(quota -> Long.valueOf(0).equals(quota.limit()))) {
            return Reason.QUOTA_UNAVAILABLE;
        }
        // A daily or unspecified quota must not be described as just a per-minute throttle.
        if ("RESOURCE_QUOTA_EXCEEDED".equals(_providerReason)
                || _quotas.stream().anyMatch(quota -> quota.id() == null || !quota.id().contains("PerMinute"))) {
            return Reason.QUOTA_EXHAUSTED;
        }
        return Reason.RATE_LIMITED;
    }

    private static void mergeMessageQuotas(String _message, List<QuotaViolation> _quotas) {
        Map<String, Set<Long>> limitsByMetric = new LinkedHashMap<>();
        Matcher matcher = MESSAGE_QUOTA.matcher(_message);
        while (matcher.find()) {
            String metric = safe(matcher.group(1), METRIC);
            Long limit = nonnegativeLong(matcher.group(2));
            if (metric == null || limit == null) continue;
            limitsByMetric.computeIfAbsent(metric, ignored -> new LinkedHashSet<>()).add(limit);
        }
        for (Map.Entry<String, Set<Long>> entry : limitsByMetric.entrySet()) {
            String metric = entry.getKey();
            List<QuotaViolation> matching = _quotas.stream().filter(quota -> metric.equals(quota.metric())).toList();
            // Supplied structured values take precedence over prose.
            if (!matching.isEmpty() && matching.stream().allMatch(quota -> quota.limit() != null)) continue;
            if (matching.size() == 1 && entry.getValue().size() == 1) {
                QuotaViolation quota = matching.getFirst();
                _quotas.set(_quotas.indexOf(quota), new QuotaViolation(metric, quota.id(), entry.getValue().iterator().next(), quota.model()));
            } else {
                // Minute/day quotas can share a metric. Preserve ambiguous values without inventing an ID association.
                for (Long limit : entry.getValue()) {
                    if (_quotas.size() >= MAX_QUOTAS) break;
                    _quotas.add(new QuotaViolation(metric, null, limit, null));
                }
            }
        }
    }

    private static String text(JsonNode _node) {
        return _node.isString() ? _node.asString() : null;
    }

    private static String safe(String _value, Pattern _pattern) {
        return _value != null && _value.length() <= 220 && _pattern.matcher(_value).matches() ? _value : null;
    }

    private static Long nonnegativeLong(JsonNode _node) {
        if (!_node.isString() && !_node.isIntegralNumber()) return null;
        return nonnegativeLong(_node.isString() ? _node.asString() : _node.toString());
    }

    private static Long nonnegativeLong(String _value) {
        if (_value == null || !_value.matches("[0-9]{1,19}")) return null;
        try {
            return Long.valueOf(_value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long retryDuration(String _value) {
        if (_value == null || !_value.matches("[0-9]{1,18}(\\.[0-9]{1,9})?s")) return null;
        try {
            return new BigDecimal(_value.substring(0, _value.length() - 1))
                    .setScale(0, RoundingMode.CEILING).longValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private static Long retryHeader(HttpHeaders _headers) {
        String value = _headers == null ? null : _headers.getFirst(HttpHeaders.RETRY_AFTER);
        Long seconds = nonnegativeLong(value);
        if (seconds != null || value == null || value.length() > 64) return seconds;
        try {
            Duration delay = Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            if (delay.isNegative()) return null;
            return delay.getSeconds() + (delay.getNano() > 0 ? 1 : 0);
        } catch (DateTimeParseException | ArithmeticException ignored) {
            return null;
        }
    }

    private static Long maximum(Long _first, Long _second) {
        if (_first == null) return _second;
        if (_second == null) return _first;
        return Math.max(_first, _second);
    }
}
