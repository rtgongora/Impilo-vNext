package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AffiliationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD-lite affiliations between an organization and facility/provider subjects.
 * Affiliations are relationship links only — the facility registry (TUSO) and
 * provider registry (VARAPI) remain the systems of record for the subjects.
 */
@Service
public class AffiliationService {

    private static final Set<String> SUBJECT_TYPES = Set.of("FACILITY", "PROVIDER");

    private final AffiliationRepository affiliationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgRegistryOutboxWriter outboxWriter;

    public AffiliationService(AffiliationRepository affiliationRepository,
                              OrganizationRepository organizationRepository,
                              OrgRegistryOutboxWriter outboxWriter) {
        this.affiliationRepository = affiliationRepository;
        this.organizationRepository = organizationRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<AffiliationEntity> list(UUID tenantId, UUID organizationId) {
        requireOrganization(tenantId, organizationId);
        return affiliationRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public AffiliationEntity create(UUID tenantId, UUID organizationId,
                                    OrgRegistryDtos.CreateAffiliationRequest request) throws Exception {
        OrganizationEntity org = requireOrganization(tenantId, organizationId);
        if (request.subjectType() == null || !SUBJECT_TYPES.contains(request.subjectType().trim().toUpperCase())) {
            throw new IllegalArgumentException("subjectType must be one of " + SUBJECT_TYPES);
        }
        if (request.subjectRef() == null || request.subjectRef().isBlank()) {
            throw new IllegalArgumentException("subjectRef is required");
        }
        if (request.affiliationType() == null || request.affiliationType().isBlank()) {
            throw new IllegalArgumentException("affiliationType is required");
        }
        AffiliationEntity affiliation = new AffiliationEntity();
        affiliation.setTenantId(tenantId);
        affiliation.setOrganizationId(org.getId());
        affiliation.setSubjectType(request.subjectType().trim().toUpperCase());
        affiliation.setSubjectRef(request.subjectRef().trim());
        affiliation.setAffiliationType(request.affiliationType().trim().toUpperCase());
        affiliation.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase());
        affiliation.setSourceChannel(request.sourceChannel());
        affiliation.setValidFrom(request.validFrom());
        affiliation.setValidTo(request.validTo());
        AffiliationEntity saved = affiliationRepository.save(affiliation);
        outboxWriter.publish(tenantId, "ORGANIZATION", org.getId().toString(),
                "affiliation", "created",
                "org-registry:affiliation:created:" + saved.getId(),
                Map.of(
                        "organizationId", org.getId().toString(),
                        "affiliationId", saved.getId().toString(),
                        "subjectType", saved.getSubjectType(),
                        "subjectRef", saved.getSubjectRef()));
        return saved;
    }

    /**
     * Record the administering affiliation for a facility whose administrator appointment went
     * ACTIVE in TUSO (IATG Wave 2, WS-E). Reuses {@link #create} — the facility-admin case is an
     * ordinary {@code FACILITY} affiliation ({@code affiliationType=FACILITY_ADMINISTRATION},
     * {@code sourceChannel=FACILITY_ADMIN_APPOINTMENT}); no new schema. TUSO stays the facility SoR
     * and the appointment SoR — this is a relationship link only. Idempotent: an existing ACTIVE
     * FACILITY_ADMINISTRATION link for the same (org, facility) is returned instead of duplicated.
     */
    @Transactional
    public AffiliationEntity recordFacilityAdminAffiliation(
            UUID tenantId, UUID organizationId,
            OrgRegistryDtos.RecordFacilityAdminAffiliationRequest request) throws Exception {
        if (request == null || request.facilityUuid() == null || request.facilityUuid().isBlank()) {
            throw new IllegalArgumentException("facilityUuid is required");
        }
        String facilityRef = request.facilityUuid().trim();
        for (AffiliationEntity existing : affiliationRepository.findByOrganizationId(organizationId)) {
            if ("FACILITY".equals(existing.getSubjectType())
                    && facilityRef.equals(existing.getSubjectRef())
                    && "FACILITY_ADMINISTRATION".equals(existing.getAffiliationType())
                    && "ACTIVE".equals(existing.getStatus())) {
                return existing;
            }
        }
        return create(tenantId, organizationId, new OrgRegistryDtos.CreateAffiliationRequest(
                "FACILITY", facilityRef, "FACILITY_ADMINISTRATION", "ACTIVE",
                "FACILITY_ADMIN_APPOINTMENT", request.validFrom(), null));
    }

    @Transactional
    public AffiliationEntity update(UUID tenantId, UUID organizationId, UUID affiliationId,
                                    OrgRegistryDtos.UpdateAffiliationRequest request) throws Exception {
        requireOrganization(tenantId, organizationId);
        AffiliationEntity affiliation = affiliationRepository
                .findByOrganizationIdAndId(organizationId, affiliationId)
                .orElseThrow(() -> new IllegalArgumentException("affiliation not found: " + affiliationId));
        if (request.status() != null && !request.status().isBlank()) {
            affiliation.setStatus(request.status().trim().toUpperCase());
        }
        if (request.validTo() != null) {
            affiliation.setValidTo(request.validTo());
        }
        AffiliationEntity saved = affiliationRepository.save(affiliation);
        outboxWriter.publish(tenantId, "ORGANIZATION", organizationId.toString(),
                "affiliation", "updated",
                "org-registry:affiliation:updated:" + saved.getId() + ":" + saved.getStatus(),
                Map.of(
                        "organizationId", organizationId.toString(),
                        "affiliationId", saved.getId().toString(),
                        "status", saved.getStatus()));
        return saved;
    }

    private OrganizationEntity requireOrganization(UUID tenantId, UUID organizationId) {
        return organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + organizationId));
    }
}
