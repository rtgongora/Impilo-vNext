package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Details of a provisional CPID entry.
 */
public record ProvisionalCpidResponse(
        UUID oCpid,
        UUID facilityId,
        String deviceFingerprint,
        String status,
        UUID canonicalCpid,
        Instant issuedAt,
        Instant reconciledAt
) {}
