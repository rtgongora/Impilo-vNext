package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;

import java.util.UUID;

/**
 * Request body for assigning a terminology pack to a scope.
 *
 * @param scopeType  the scope level (TENANT, FACILITY, WORKSPACE)
 * @param scopeId    the identifier of the scope entity
 * @param packId     the pack to assign
 * @param packVersion the version of the pack being assigned
 * @param policyMode the enforcement policy mode (defaults to LENIENT if null)
 */
public record AssignPackRequest(
        @NotNull ScopeType scopeType,
        @NotNull UUID scopeId,
        @NotNull UUID packId,
        @NotBlank String packVersion,
        PolicyMode policyMode
) {}
