package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to issue a new step-up challenge.
 */
public record StepUpChallengeRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String challengeType
) {}
