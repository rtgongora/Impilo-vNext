package zw.gov.mohcc.impilo.abis.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;

/**
 * Extract a biometric template from a raw capture image (enrolment-from-image).
 * {@code sampleBase64} is the capture: an encoded image (PNG/JPEG/WSQ) or, when
 * {@code width×height} are given, raw 8-bit grayscale pixels. No subject_ref /
 * Health ID — extraction is pure feature computation.
 */
public record ExtractTemplateRequest(
        @NotNull BiometricModality modality,
        @NotBlank String sampleBase64,
        Integer width,
        Integer height,
        Integer dpi) {
}
