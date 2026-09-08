package com.oibieldev.gamedev_api.dto.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics.QuotaViolation;

class ImageProviderDiagnosticsTests {

    @Test
    void keepsSnapshotOfQuotaViolationsAndPreventsExternalMutation() {
        QuotaViolation violation = new QuotaViolation(
                "generativelanguage.googleapis.com/generate_content_paid_tier_requests",
                "GenerateRequestsPerDayPerProjectPerModel-PaidTier", 1000L, "gemini-3.1-flash-image");
        List<QuotaViolation> quotas = new ArrayList<>(List.of(violation));
        ImageProviderDiagnostics diagnostics = new ImageProviderDiagnostics(
                429, "RESOURCE_EXHAUSTED", null, "gemini-3.1-flash-image", quotas, null);

        quotas.clear();

        assertEquals(List.of(violation), diagnostics.quotas());
        assertThrows(UnsupportedOperationException.class, () -> diagnostics.quotas().clear());
    }
}
