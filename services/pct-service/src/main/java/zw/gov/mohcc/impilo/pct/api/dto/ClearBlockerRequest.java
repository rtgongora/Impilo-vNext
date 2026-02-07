package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to clear a discharge blocker.
 *
 * <p>Each blocker represents a clearance gate that must be satisfied
 * before the patient can complete their discharge. Supported blocker
 * names include: CLINICAL, BILLING, PHARMACY, PAYMENT, SUMMARY.</p>
 *
 * @param blockerName the name of the blocker to clear
 */
public record ClearBlockerRequest(
        @NotBlank String blockerName
) {}
