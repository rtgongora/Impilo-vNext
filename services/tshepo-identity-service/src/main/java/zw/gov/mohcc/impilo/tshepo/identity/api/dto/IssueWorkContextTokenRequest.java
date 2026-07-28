package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to issue a duty-scoped WORK_CONTEXT token (D-P3): a provider's daily
 * work session bound to a facility/workspace context. The BFF proves the
 * assignment against Vashandi (or the relevant SoR for the mode) BEFORE
 * calling; this service binds the proven context into a short-lived
 * revocable token. Live professional scope, supervision and registry status
 * are PDP-resolved per action — never in-token.
 *
 * @param workMode a {@link zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode}
 *                 name (Phase B). Required: an unparseable or missing mode is
 *                 a 400, never a default — a defaulted mode would silently
 *                 mint the platform's most permissive session. Drives which
 *                 anchor (facilityId / organisationId / jurisdictionCode+
 *                 organisationId / programmeId+organisationId) is required —
 *                 see {@code TokenIssuanceService.assertAnchorPresent}.
 * @param facilityId required only for FACILITY / FACILITY_OR_ORGANISATION
 *                   modes — nullable (a regulatory/programme/jurisdiction/
 *                   support token carries no facility).
 * @param previousJti on a context or mode switch, the jti of the session's
 *                    current work token — revoked atomically before reissue
 *                    (a switch is always a NEW token, never a mutation)
 */
public record IssueWorkContextTokenRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        String providerPublicId,
        @NotBlank String workMode,
        UUID facilityId,
        // Operational-context ids are opaque strings, not UUIDs: departmentId/wardId/
        // programmeId are Vashandi VARCHAR keys (a UUID field would 400 on a non-UUID
        // dept id and silently break the binding). Kept as String on the wire.
        String departmentId,
        String wardId,
        String programmeId,
        String organisationId,
        // Jurisdiction the regulatory appointment covers (NATIONAL, or a province/district code
        // from the zibo value set). Carried on the token because a regulator's authority is
        // bounded by WHERE as well as by which organisation: an inspector appointed for one
        // province must not act across the country merely because their council is national.
        String jurisdictionCode,
        String assignmentId,
        UUID workspaceId,
        String servicePointId,
        // The resolved work context this token was proven against (Phase C
        // ResolvedWorkContext.contextId / contextKind). Nullable until the
        // Phase C resolver is wired; not yet consumed downstream.
        String contextId,
        String contextKind,
        String roleTemplateId,
        String purposeOfUse,
        String sessionAssurance,
        String previousJti,
        Integer ttlSeconds
) {}
