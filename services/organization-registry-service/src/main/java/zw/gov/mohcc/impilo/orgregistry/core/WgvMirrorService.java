package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgType;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One-way import of {@code wgv_organisation} records from workforce-governance.
 *
 * <p>Wave-1 dual-SoR strategy: workforce-governance remains the system of record
 * for its existing organisations; this service holds a read-only mirror row
 * ({@code source = WGV_MIRROR}) whose {@code source_ref} back-points to
 * {@code wgv_organisation.id}. Upserts are idempotent on {@code source_ref} —
 * re-mirroring the same wgv organisation updates the existing row.
 */
@Service
public class WgvMirrorService {

    public static final String SOURCE_WGV_MIRROR = "WGV_MIRROR";

    private final OrganizationRepository organizationRepository;
    private final OrgRegistryOutboxWriter outboxWriter;

    public WgvMirrorService(OrganizationRepository organizationRepository,
                            OrgRegistryOutboxWriter outboxWriter) {
        this.organizationRepository = organizationRepository;
        this.outboxWriter = outboxWriter;
    }

    public record MirrorResult(OrganizationEntity organization, boolean created) {
    }

    @Transactional
    public MirrorResult mirror(UUID tenantId, OrgRegistryDtos.WgvOrganisationPayload payload) throws Exception {
        if (payload.id() == null) {
            throw new IllegalArgumentException("id (wgv_organisation id) is required");
        }
        if (payload.code() == null || payload.code().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (payload.legalName() == null || payload.legalName().isBlank()) {
            throw new IllegalArgumentException("legalName is required");
        }
        String sourceRef = payload.id().toString();
        OrganizationEntity existing = organizationRepository
                .findBySourceAndSourceRef(SOURCE_WGV_MIRROR, sourceRef)
                .orElse(null);
        boolean created = existing == null;
        OrganizationEntity org = created ? new OrganizationEntity() : existing;
        if (created) {
            org.setTenantId(tenantId);
            org.setSource(SOURCE_WGV_MIRROR);
            org.setSourceRef(sourceRef);
            org.setCode(payload.code().trim());
        }
        org.setLegalName(payload.legalName().trim());
        org.setOrgType(mapOrgType(payload.organisationType()));
        org.setStatus(mapStatus(payload.status()));
        // wgv organisations are already governance-managed; the mirror carries
        // that trust rather than re-running Channel-A/B verification.
        org.setVerificationStatus(VerificationStatus.VERIFIED);
        org.setWgvOrgTypeRaw(payload.organisationType());
        org.setWgvStatusRaw(payload.status());
        org.setWgvParentSourceRef(payload.parentOrganisationId() != null
                ? payload.parentOrganisationId().toString() : null);
        org.setMirroredAt(OffsetDateTime.now());
        OrganizationEntity saved = organizationRepository.save(org);
        outboxWriter.publish(tenantId, "ORGANIZATION", saved.getId().toString(),
                "organization", created ? "mirrored" : "mirror_updated",
                "org-registry:organization:mirror:" + sourceRef + ":" + System.nanoTime(),
                Map.of(
                        "organizationId", saved.getId().toString(),
                        "sourceRef", sourceRef,
                        "created", created));
        return new MirrorResult(saved, created);
    }

    static OrgType mapOrgType(String wgvOrganisationType) {
        if (wgvOrganisationType == null || wgvOrganisationType.isBlank()) {
            return OrgType.OTHER;
        }
        String normalized = wgvOrganisationType.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return OrgType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return switch (normalized) {
                case "MOHCC", "MINISTRY", "GOVERNMENT" -> OrgType.GOVERNMENT_HEALTH_SERVICE;
                case "COUNCIL", "REGULATOR" -> OrgType.STATUTORY_REGULATOR;
                case "MISSION", "FAITH_BASED" -> OrgType.MISSION_FAITH_BASED;
                case "NGO", "IMPLEMENTING_PARTNER" -> OrgType.NGO_IMPLEMENTING_PARTNER;
                case "ACADEMIC", "TRAINING_INSTITUTION" -> OrgType.ACADEMIC_TRAINING;
                case "PRIVATE", "PRIVATE_PROVIDER" -> OrgType.PRIVATE_PROVIDER_GROUP;
                default -> OrgType.OTHER;
            };
        }
    }

    static String mapStatus(String wgvStatus) {
        if (wgvStatus == null || wgvStatus.isBlank()) {
            return "ACTIVE";
        }
        return wgvStatus.trim().toUpperCase();
    }
}
