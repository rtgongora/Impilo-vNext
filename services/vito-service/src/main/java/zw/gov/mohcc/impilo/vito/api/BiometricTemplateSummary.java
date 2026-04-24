package zw.gov.mohcc.impilo.vito.api;

import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Safe projection of an enrolled template — no raw biometric bytes or hashes.
 */
public record BiometricTemplateSummary(
        Long id,
        UUID healthId,
        BiometricModality modality,
        String position,
        Integer qualityScore,
        String status,
        OffsetDateTime capturedAt,
        UUID profileId,
        String vendorEngine,
        boolean livenessSupported) {

    public static BiometricTemplateSummary fromEntity(BiometricTemplateEntity t) {
        return new BiometricTemplateSummary(
                t.getId(),
                t.getHealthId(),
                t.getModality(),
                t.getPosition(),
                t.getQualityScore(),
                t.getStatus(),
                t.getCapturedAt(),
                t.getProfileId(),
                t.getVendorEngine(),
                t.isLivenessSupported());
    }
}
