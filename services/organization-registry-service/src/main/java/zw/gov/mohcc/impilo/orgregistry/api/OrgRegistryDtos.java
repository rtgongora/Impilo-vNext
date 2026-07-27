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

    /** Attach an already stored document-service image object as the organisation logo. */
    public record UpdateOrganizationLogoRequest(String logoObjectId) {
    }

    /** Create a regulatory appointment (ROM-W1) — lands PENDING_VERIFICATION. */
    public record CreateAppointmentRequest(
            String personHealthId,
            String roleCode,
            String jurisdictionCode,
            String source,
            String evidenceRef,
            String appointedBy,
            LocalDate validFrom,
            LocalDate validTo) {
    }

    /** End/revoke an appointment. {@code reason} = "REVOKED" marks revocation, else ENDED. */
    public record EndAppointmentRequest(String reason) {
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

    /**
     * Record the administering relationship for a facility whose administrator appointment went
     * ACTIVE in TUSO (IATG Wave 2, WS-E). {@code facilityUuid} is the canonical TUSO facility UUID;
     * {@code personHealthId}/{@code appointedBy} are provenance (the affiliation itself is org↔facility).
     */
    public record RecordFacilityAdminAffiliationRequest(
            String facilityUuid,
            String personHealthId,
            String appointedBy,
            LocalDate validFrom) {
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

    /**
     * Issue an organisation-invitation. {@code inviteeIdentifier} is an email or Health ID
     * ({@code inviteeIdentifierType} = EMAIL | HEALTH_ID); {@code role} is the role being
     * offered; {@code facilityUuid} scopes a facility-administrator invitation;
     * {@code expiresInHours} bounds acceptance (defaulted server-side when null).
     */
    public record CreateInvitationRequest(
            UUID invitedByRepId,
            /**
             * Health ID of the inviter when they are a regulatory appointment-holder administrator
             * (RB-6) rather than an authorized representative. One of {@code invitedByRepId} /
             * {@code invitedByHealthId} must be present.
             */
            String invitedByHealthId,
            String inviteeIdentifier,
            String inviteeIdentifierType,
            String role,
            /**
             * Optional governed appointment role (V010). When set to a regulatory role, acceptance
             * mints a PENDING_VERIFICATION regulatory appointment (source=INVITATION) rather than an
             * affiliation. Null keeps the legacy free-text {@code role} affiliation path.
             */
            String roleCode,
            UUID facilityUuid,
            Integer expiresInHours) {
    }

    /** Accept a pending invitation by its single-use token. */
    public record AcceptInvitationRequest(String token, String acceptedByHealthId) {
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

    /**
     * One row of the read-only WGV mirror inventory (source = WGV_MIRROR).
     * Consumed by the workforce-governance reconciliation report to diff the
     * mirror against local active {@code wgv_organisation} rows.
     *
     * <p>{@code orgRegistryId} is this registry's canonical organization id and
     * {@code sourceRef} back-points to {@code wgv_organisation.id}; together they
     * form the dual-key mapping the phase-2c key backfill resolves.
     */
    public record MirrorInventoryItem(
            UUID orgRegistryId,
            String sourceRef,
            String code,
            String legalName,
            String status,
            String verificationStatus) {
    }

    /** One page of the WGV mirror inventory. */
    public record MirrorInventoryPage(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<MirrorInventoryItem> items) {
    }
}
