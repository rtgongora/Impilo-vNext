package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * C3 silent identifier-resolution request (see shared-read-models.md § C3).
 *
 * Resolves a login-supplied identifier to a person anchor (Health ID) so the
 * person-first login flow never depends on the user knowing their Health ID.
 *
 * <p><b>Invariants:</b> resolution is <i>silent</i> (the chain
 * Tshepo→Varapi→Vashandi→VITO→TUSO is internal and never surfaced) and
 * <i>anti-enumeration</i> (the response never reveals existence on failure;
 * uniform shape + constant-time floor). {@code PROVIDER_ID} / {@code COUNCIL_NUMBER}
 * resolve a profile but <b>never authenticate</b> (policy LOGIN-PROVIDERID-DENY).</p>
 */
public record IdentifierResolveRequest(
        @NotNull UUID tenantId,
        @NotBlank String kind,   // HEALTH_ID | PHONE | EMAIL | IMPILO_ID | PROVIDER_ID | COUNCIL_NUMBER | INVITE
        @NotBlank String value
) {
}
