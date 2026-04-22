package zw.gov.mohcc.impilo.indawo.api.dto;

import java.util.UUID;

/**
 * Lightweight site legitimacy summary for cross-service gating.
 */
public record SiteStatusSummary(
        UUID siteId,
        String siteCode,
        String name,
        String status,
        String regulatoryStatus,
        String operationalStatus,
        boolean active
) {}

