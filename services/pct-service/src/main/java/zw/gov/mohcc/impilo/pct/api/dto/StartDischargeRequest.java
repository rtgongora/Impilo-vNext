package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to initiate the discharge workflow for a patient journey.
 *
 * <p>Starting a discharge creates a discharge case with appropriate
 * clearance gates (clinical, billing, pharmacy, payment, summary)
 * that must all be cleared before the patient can be discharged.</p>
 *
 * @param dischargeType the type of discharge (e.g. CLINICAL, AMA, TRANSFER)
 */
public record StartDischargeRequest(
        @NotBlank String dischargeType
) {}
