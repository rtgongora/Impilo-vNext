package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to create or update a policy rule.
 */
public record PolicyRuleRequest(
        @NotNull UUID tenantId,
        @NotBlank String name,
        String description,
        String actorType,
        String role,
        String resourceType,
        String action,
        String purpose,
        boolean facilityScope,
        boolean workspaceScope,
        @NotBlank String effect,
        int priority,
        String conditions,
        boolean active
) {}
