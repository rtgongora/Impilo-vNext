package zw.gov.mohcc.impilo.abis.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;

/**
 * 1:1 verification request — routine after a candidate has been identified.
 */
public record VerifyRequest(
        @NotBlank @Size(max = 128) String subjectRef,
        @NotNull BiometricModality modality,
        @Size(max = 32) String position,
        @NotBlank String probeBase64,
        Double livenessScore,
        String deviceAttestationRef) {
}
