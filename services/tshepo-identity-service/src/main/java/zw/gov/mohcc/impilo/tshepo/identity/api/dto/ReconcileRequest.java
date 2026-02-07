package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to reconcile a provisional O-CPID to a canonical CPID.
 *
 * <p>Once a facility comes back online, the O-CPID must be mapped to the real
 * CPID derived from the now-resolved Health ID.</p>
 */
public record ReconcileRequest(
        @NotNull UUID tenantId,
        @NotNull UUID oCpid,
        @NotNull UUID healthId
) {}
