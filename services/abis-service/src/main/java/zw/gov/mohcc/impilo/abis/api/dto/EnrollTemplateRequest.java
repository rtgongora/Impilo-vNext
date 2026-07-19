package zw.gov.mohcc.impilo.abis.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;

/**
 * Enroll (or re-enroll) a biometric template. {@code subjectRef} is an opaque ABIS subject
 * reference — never a Health ID.
 */
public record EnrollTemplateRequest(
        @NotBlank @Size(max = 128) String subjectRef,
        @NotNull BiometricModality modality,
        @Size(max = 32) String position,
        Integer qualityScore,
        @Size(max = 64) String algorithmVersion,
        @NotBlank String templateBase64) {
}
