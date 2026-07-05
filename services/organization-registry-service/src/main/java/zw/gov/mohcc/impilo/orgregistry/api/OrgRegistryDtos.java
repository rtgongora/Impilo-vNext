package zw.gov.mohcc.impilo.orgregistry.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrgRegistryDtos {

    private OrgRegistryDtos() {
    }

    public record CreateOrganizationRequest(
            String code,
            String legalName,
            String orgType,
            UUID countryOperationRef,
            Map<String, Object> registrationIdentifiers) {
    }

    public record AddRepresentativeRequest(
            String personHealthId,
            String roleTitle,
            String evidenceRef,
            LocalDate validFrom,
            LocalDate validTo,
            String appointedBy) {
    }

    /**
     * National-admin verification action. {@code verifiedRepresentativeIds}
     * are representatives attested as VERIFIED as part of this action.
     */
    public record VerifyOrganizationRequest(List<UUID> verifiedRepresentativeIds) {
    }

    public record CreateAffiliationRequest(
            String subjectType,
            String subjectRef,
            String affiliationType,
            String status,
            String sourceChannel,
            LocalDate validFrom,
            LocalDate validTo) {
    }

    public record UpdateAffiliationRequest(String status, LocalDate validTo) {
    }

    public record CreateClaimRequest(
            UUID submittedByRepId,
            String subjectHealthId,
            String claimedRole,
            String trustBasis,
            Map<String, Object> evidence) {
    }

    public record TransitionClaimRequest(String status, String adjudicationRef) {
    }

    /** wgv_organisation-shaped payload for the one-way mirror endpoint. */
    public record WgvOrganisationPayload(
            UUID id,
            String code,
            String legalName,
            String organisationType,
            String status,
            UUID parentOrganisationId) {
    }
}
