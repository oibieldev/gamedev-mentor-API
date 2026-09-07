package com.oibieldev.gamedev_api.dto.image;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Filtered provider facts; never contains prompts, credentials or raw error messages. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageProviderDiagnostics(
        int httpStatus,
        String providerStatus,
        String providerReason,
        String model,
        List<QuotaViolation> quotas,
        Long retryAfterSeconds
) {

    public ImageProviderDiagnostics {
        quotas = List.copyOf(quotas);
    }

    public boolean hasFreeTierQuota() {
        return quotas.stream().anyMatch(quota ->
                (quota.metric() != null && quota.metric().contains("free_tier"))
                        || (quota.id() != null && quota.id().contains("FreeTier")));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuotaViolation(String metric, String id, Long limit, String model) {
    }
}
