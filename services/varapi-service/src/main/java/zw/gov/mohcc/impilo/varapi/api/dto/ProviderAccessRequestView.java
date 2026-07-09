package zw.gov.mohcc.impilo.varapi.api.dto;

import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAccessRequestEntity;

import java.time.Instant;

/**
 * Applicant-facing projection of a provider-access request. Council/EC numbers
 * are already masked in the entity; the Provider ID (if any) is emitted masked.
 */
public record ProviderAccessRequestView(
        String publicId,
        String requestType,
        String status,
        String nextActor,
        String profession,
        String councilCode,
        String councilNumberMasked,
        String ecNumberMasked,
        String organizationRef,
        String evidenceSummary,
        String reason,
        String providerPublicId,
        String decidedBy,
        Instant decidedAt,
        String decisionNote,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProviderAccessRequestView of(ProviderAccessRequestEntity e) {
        return new ProviderAccessRequestView(
                e.getPublicId(),
                e.getRequestType(),
                e.getStatus(),
                e.getNextActor(),
                e.getProfession(),
                e.getCouncilCode(),
                e.getCouncilNumberMasked(),
                e.getEcNumberMasked(),
                e.getOrganizationRef(),
                e.getEvidenceSummary(),
                e.getReason(),
                mask(e.getProviderPublicId()),
                e.getDecidedBy(),
                e.getDecidedAt(),
                e.getDecisionNote(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 4 ? trimmed + "***" : trimmed.substring(0, 4) + "***";
    }
}
