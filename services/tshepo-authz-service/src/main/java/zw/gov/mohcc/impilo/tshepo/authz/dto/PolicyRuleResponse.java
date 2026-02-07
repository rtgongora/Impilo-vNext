package zw.gov.mohcc.impilo.tshepo.authz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Policy rule response for the policy management API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolicyRuleResponse(
        Long id,
        UUID tenantId,
        String name,
        String description,
        String actorType,
        String role,
        String resourceType,
        String action,
        String purpose,
        boolean facilityScope,
        boolean workspaceScope,
        String effect,
        int priority,
        String conditions,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
