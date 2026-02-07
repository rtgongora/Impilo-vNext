package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to resolve an Impilo ID through the full identity chain:
 * Impilo ID → Health ID (via VITO lookup hash) → CPID.
 *
 * <p>The impiloIdHash is a verifier/lookup hash — never the plaintext Impilo ID.</p>
 */
public record ResolveRequest(
        @NotNull UUID tenantId,
        @NotBlank String impiloIdHash
) {}
