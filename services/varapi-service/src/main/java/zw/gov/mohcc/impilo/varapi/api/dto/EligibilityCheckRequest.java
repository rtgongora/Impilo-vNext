package zw.gov.mohcc.impilo.varapi.api.dto;

import java.util.UUID;

public record EligibilityCheckRequest(
        String providerPublicId,
        String vaToken,
        UUID tenantId,
        Long facilityId,
        UUID workspaceId
) {}
