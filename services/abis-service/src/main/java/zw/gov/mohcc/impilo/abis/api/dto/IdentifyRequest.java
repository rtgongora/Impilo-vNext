package zw.gov.mohcc.impilo.abis.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;

/**
 * 1:N identification request. Restricted (X-Identify-Reason header) to
 * ENROLMENT / RECOVERY / DEDUPLICATION per Identity Contract §11.
 */
public record IdentifyRequest(
        @NotNull BiometricModality modality,
        @NotBlank String probeBase64,
        Double livenessScore,
        String deviceAttestationRef) {
}
