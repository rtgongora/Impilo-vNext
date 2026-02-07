package zw.gov.mohcc.impilo.tshepo.authz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response after issuing or querying a step-up challenge.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepUpChallengeResponse(
        UUID challengeId,
        String challengeType,
        String status,
        Instant issuedAt,
        Instant expiresAt,
        Instant completedAt
) {}
