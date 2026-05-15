package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Canonical prescription create request.
 */
public record CreatePrescriptionRequest(
        @NotBlank String patient_id,
        String encounter_id,
        String facility_id,
        @NotBlank String medication_name,
        String generic_name,
        @NotBlank String dosage,
        String route,
        @NotBlank String frequency,
        String duration,
        Integer quantity,
        String instructions,
        String indication,
        @NotBlank String prescribed_by
) {}
