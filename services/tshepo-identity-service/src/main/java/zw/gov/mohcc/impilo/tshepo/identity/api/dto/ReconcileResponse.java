package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of an O-CPID reconciliation.
 */
public record ReconcileResponse(
        UUID oCpid,
        UUID canonicalCpid,
        String status,
        Instant reconciledAt
) {}
