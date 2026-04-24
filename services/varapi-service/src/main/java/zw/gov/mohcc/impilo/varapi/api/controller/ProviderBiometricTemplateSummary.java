package zw.gov.mohcc.impilo.varapi.api.controller;

import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricModality;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;

import java.time.Instant;
import java.util.UUID;

public record ProviderBiometricTemplateSummary(
        Long id,
        Long providerId,
        ProviderBiometricModality modality,
        String position,
        Integer qualityScore,
        String status,
        Instant capturedAt,
        UUID profileId,
        String vendorEngine,
        boolean livenessSupported) {

    static ProviderBiometricTemplateSummary from(ProviderBiometricTemplateEntity t) {
        return new ProviderBiometricTemplateSummary(
                t.getId(),
                t.getProviderId(),
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
