package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.util.UUID;

/**
 * Feed row for {@code WorkContextRevalidationJob} (experience-bff, Phase C
 * C4): the minimum a caller needs to re-prove a live WORK_CONTEXT token's
 * context against source and revoke it if the proof now fails.
 */
public record ActiveWorkContextTokenSummary(
        String jti,
        String actorId,
        UUID tenantId,
        String contextId,
        String workMode
) {}
