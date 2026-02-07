package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for applying a drug substitution to a dispense item.
 *
 * @param targetDrugCode the replacement drug code
 * @param reason         the reason for the substitution
 */
public record SubstituteRequest(
        @NotBlank String targetDrugCode,
        @NotBlank String reason
) {}
