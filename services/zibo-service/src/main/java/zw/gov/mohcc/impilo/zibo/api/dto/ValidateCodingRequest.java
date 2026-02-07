package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;

import java.util.UUID;

/**
 * Request body for validating a single FHIR coding.
 *
 * @param system     the coding system URI (canonical URL of the CodeSystem)
 * @param code       the code to validate
 * @param display    the display text (optional, informational)
 * @param mode       optional policy mode override (STRICT or LENIENT)
 * @param facilityId optional facility context for policy resolution
 */
public record ValidateCodingRequest(
        @NotBlank String system,
        @NotBlank String code,
        String display,
        PolicyMode mode,
        UUID facilityId
) {}
