package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;

/**
 * Request DTO for creating a pickup proof for a dispense order.
 *
 * @param method      the verification method (OTP, BIOMETRIC, ID_CHECK, DELEGATED, WAIVER)
 * @param delegatedTo the delegate identifier (required when method is DELEGATED)
 */
public record PickupCreateRequest(
        @NotNull PickupMethod method,
        String delegatedTo
) {}
