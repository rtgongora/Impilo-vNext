package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to issue a duty-scoped WORK_CONTEXT token (D-P3): a provider's daily
 * work session bound to a facility/workspace context. The BFF proves the
 * assignment against Vashandi BEFORE calling; this service binds the proven
 * context into a short-lived revocable token. Live professional scope,
 * supervision and registry status are PDP-resolved per action — never in-token.
 *
 * @param previousJti on a facility/workspace switch, the jti of the session's
 *                    current work token — revoked atomically before reissue
 *                    (a context switch is a NEW token, never a mutation)
 */
public record IssueWorkContextTokenRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String providerPublicId,
        @NotNull UUID facilityId,
        UUID departmentId,
        UUID workspaceId,
        String roleTemplateId,
        String purposeOfUse,
        String sessionAssurance,
        String previousJti,
        Integer ttlSeconds
) {}
