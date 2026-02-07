package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to verify (complete) a step-up challenge.
 */
public record StepUpVerifyRequest(
        @NotNull UUID challengeId,
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String verificationCode
) {}
