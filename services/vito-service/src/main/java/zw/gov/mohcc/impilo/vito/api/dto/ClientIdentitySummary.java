package zw.gov.mohcc.impilo.vito.api.dto;

import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;

import java.util.UUID;

/**
 * Lightweight client identity legitimacy summary for cross-service gating.
 */
public record ClientIdentitySummary(
        UUID healthId,
        IdentityStatus identityStatus,
        ClientVerificationState verificationStatus,
        int identityAssuranceLevel
) {}

