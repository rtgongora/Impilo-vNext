package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;

/**
 * Request DTO for creating a pickup proof for a dispense order.
 *
 * @param method         the verification method (OTP, QR, BIOMETRIC, SMS_REFERENCE)
 * @param delegatedTo    the delegate identifier (for delegate collection)
 * @param namedRecipient OF-B16: the named person authorised to collect
 *                       (required for SMS_REFERENCE — staff verify identity
 *                       against this name at handover)
 */
public record PickupCreateRequest(
        @NotNull PickupMethod method,
        String delegatedTo,
        String namedRecipient
) {}
