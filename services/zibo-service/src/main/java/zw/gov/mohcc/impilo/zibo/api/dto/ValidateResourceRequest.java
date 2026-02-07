package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;

import java.util.UUID;

/**
 * Request body for validating all codings within a FHIR resource.
 *
 * @param resourceJson the FHIR resource JSON to validate
 * @param mode         optional policy mode override
 * @param facilityId   optional facility context for policy resolution
 */
public record ValidateResourceRequest(
        @NotNull Object resourceJson,
        PolicyMode mode,
        UUID facilityId
) {}
