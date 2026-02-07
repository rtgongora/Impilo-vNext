package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.util.UUID;

/**
 * Result of a full identity resolution: Impilo ID → Health ID → CPID.
 */
public record ResolveResponse(
        UUID healthId,
        UUID cpid,
        UUID crid,
        String mappingStatus
) {}
