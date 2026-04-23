package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.time.Instant;
import java.util.UUID;

public record VisibilityEscalationGrantResponse(
        UUID grantToken,
        String visibilityCeiling,
        String workflowType,
        Instant expiresAt,
        long requestId
) {}
