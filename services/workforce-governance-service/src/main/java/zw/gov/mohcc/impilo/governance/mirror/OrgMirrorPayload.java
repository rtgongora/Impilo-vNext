package zw.gov.mohcc.impilo.governance.mirror;

import java.util.UUID;

import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;

/**
 * Wire payload posted to the organization-registry mirror receiver
 * ({@code POST /v1/internal/org-registry/mirror/wgv}).
 *
 * <p>Field names and shape MUST match the receiver's
 * {@code OrgRegistryDtos.WgvOrganisationPayload} — this producer must never
 * change the receiver contract. The receiver upserts idempotently on
 * {@code id} (the wgv organisation id, stored as {@code source_ref}).
 */
public record OrgMirrorPayload(
        UUID id,
        String code,
        String legalName,
        String organisationType,
        String status,
        UUID parentOrganisationId) {

    public static OrgMirrorPayload from(OrganisationEntity org) {
        return new OrgMirrorPayload(
                org.getId(),
                org.getOrganisationCode(),
                org.getLegalName(),
                org.getOrganisationType(),
                org.getStatus(),
                org.getParentOrganisationId());
    }
}
