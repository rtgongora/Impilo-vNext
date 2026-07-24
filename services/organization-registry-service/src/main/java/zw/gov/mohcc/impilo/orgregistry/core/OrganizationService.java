package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AuthorizedRepresentativeEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgType;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AuthorizedRepresentativeRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Organization onboarding lifecycle: DRAFT creation, authorized representatives,
 * submit-verification, and national-admin verification (which requires at least
 * one VERIFIED authorized representative).
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final AuthorizedRepresentativeRepository representativeRepository;
    private final OrgRegistryOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public OrganizationService(OrganizationRepository organizationRepository,
                               AuthorizedRepresentativeRepository representativeRepository,
                               OrgRegistryOutboxWriter outboxWriter,
                               ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.representativeRepository = representativeRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrganizationEntity createDraft(UUID tenantId, OrgRegistryDtos.CreateOrganizationRequest request,
                                          String createdBy) throws Exception {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (request.legalName() == null || request.legalName().isBlank()) {
            throw new IllegalArgumentException("legalName is required");
        }
        if (request.orgType() == null || request.orgType().isBlank()) {
            throw new IllegalArgumentException("orgType is required");
        }
        OrgType orgType;
        try {
            orgType = OrgType.valueOf(request.orgType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("orgType must be one of " + List.of(OrgType.values()));
        }
        String code = request.code().trim();
        if (organizationRepository.findByCode(code).isPresent()) {
            throw new IllegalStateException("organization code already exists: " + code);
        }
        OrganizationEntity org = new OrganizationEntity();
        org.setTenantId(tenantId);
        org.setCode(code);
        org.setLegalName(request.legalName().trim());
        org.setOrgType(orgType);
        org.setCountryOperationRef(request.countryOperationRef());
        if (request.registrationIdentifiers() != null) {
            org.setRegistrationIdentifiers(objectMapper.writeValueAsString(request.registrationIdentifiers()));
        }
        org.setStatus("DRAFT");
        org.setVerificationStatus(VerificationStatus.UNVERIFIED);
        org.setSource("NATIVE");
        OrganizationEntity saved = organizationRepository.save(org);
        outboxWriter.publish(tenantId, "ORGANIZATION", saved.getId().toString(),
                "organization", "created",
                "org-registry:organization:created:" + saved.getId(),
                Map.of(
                        "organizationId", saved.getId().toString(),
                        "code", saved.getCode(),
                        "orgType", saved.getOrgType().name(),
                        "createdBy", createdBy == null ? "system" : createdBy));
        return saved;
    }

    public List<OrganizationEntity> list(UUID tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return organizationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                    tenantId, status.trim().toUpperCase());
        }
        return organizationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public OrganizationEntity get(UUID tenantId, UUID id) {
        return organizationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + id));
    }

    /**
     * Resolve either this registry's canonical id or the source id of a WGV mirror.
     * Both paths remain tenant-scoped.
     */
    public OrganizationEntity getByCanonicalOrSourceId(UUID tenantId, UUID id) {
        return organizationRepository.findByTenantIdAndId(tenantId, id)
                .or(() -> organizationRepository.findByTenantIdAndSourceAndSourceRef(
                        tenantId, WgvMirrorService.SOURCE_WGV_MIRROR, id.toString()))
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + id));
    }

    @Transactional
    public OrganizationEntity updateLogo(UUID tenantId, UUID organizationId, String logoObjectId,
                                         String updatedBy) throws Exception {
        if (logoObjectId == null || logoObjectId.isBlank()) {
            throw new IllegalArgumentException("logoObjectId is required");
        }
        UUID.fromString(logoObjectId.trim());
        OrganizationEntity org = getByCanonicalOrSourceId(tenantId, organizationId);
        org.setLogoObjectId(logoObjectId.trim());
        OrganizationEntity saved = organizationRepository.save(org);
        outboxWriter.publish(tenantId, "ORGANIZATION", saved.getId().toString(),
                "organization", "logo_updated",
                "org-registry:organization:logo-updated:" + saved.getId() + ":" + System.nanoTime(),
                Map.of(
                        "organizationId", saved.getId().toString(),
                        "logoObjectId", saved.getLogoObjectId(),
                        "updatedBy", updatedBy == null ? "system" : updatedBy));
        return saved;
    }

    @Transactional
    public AuthorizedRepresentativeEntity addRepresentative(UUID tenantId, UUID organizationId,
                                                            OrgRegistryDtos.AddRepresentativeRequest request,
                                                            String appointedBy) throws Exception {
        OrganizationEntity org = get(tenantId, organizationId);
        if (request.personHealthId() == null || request.personHealthId().isBlank()) {
            throw new IllegalArgumentException("personHealthId is required");
        }
        AuthorizedRepresentativeEntity rep = new AuthorizedRepresentativeEntity();
        rep.setTenantId(tenantId);
        rep.setOrganizationId(org.getId());
        rep.setPersonHealthId(request.personHealthId().trim());
        rep.setRoleTitle(request.roleTitle());
        rep.setEvidenceRef(request.evidenceRef());
        rep.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        rep.setValidFrom(request.validFrom());
        rep.setValidTo(request.validTo());
        rep.setAppointedBy(request.appointedBy() != null ? request.appointedBy() : appointedBy);
        AuthorizedRepresentativeEntity saved = representativeRepository.save(rep);
        outboxWriter.publish(tenantId, "ORGANIZATION", org.getId().toString(),
                "authorized_representative", "added",
                "org-registry:representative:added:" + saved.getId(),
                Map.of(
                        "organizationId", org.getId().toString(),
                        "representativeId", saved.getId().toString(),
                        "personHealthId", saved.getPersonHealthId()));
        return saved;
    }

    public List<AuthorizedRepresentativeEntity> listRepresentatives(UUID tenantId, UUID organizationId) {
        OrganizationEntity org = get(tenantId, organizationId);
        return representativeRepository.findByOrganizationId(org.getId());
    }

    @Transactional
    public OrganizationEntity submitVerification(UUID tenantId, UUID organizationId, String submittedBy)
            throws Exception {
        OrganizationEntity org = get(tenantId, organizationId);
        if (org.getVerificationStatus() != VerificationStatus.UNVERIFIED
                && org.getVerificationStatus() != VerificationStatus.REJECTED) {
            throw new IllegalStateException("organization is not in a submittable verification state: "
                    + org.getVerificationStatus());
        }
        if (representativeRepository.findByOrganizationId(org.getId()).isEmpty()) {
            throw new IllegalStateException("organization must have at least one authorized representative before submitting for verification");
        }
        org.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        OrganizationEntity saved = organizationRepository.save(org);
        outboxWriter.publish(tenantId, "ORGANIZATION", saved.getId().toString(),
                "organization", "verification_submitted",
                "org-registry:organization:verification-submitted:" + saved.getId(),
                Map.of(
                        "organizationId", saved.getId().toString(),
                        "submittedBy", submittedBy == null ? "system" : submittedBy));
        return saved;
    }

    /**
     * National-admin action. Records the verifier and requires at least one
     * VERIFIED authorized representative (representatives listed in
     * {@code verifiedRepresentativeIds} are attested as part of this action).
     */
    @Transactional
    public OrganizationEntity verify(UUID tenantId, UUID organizationId, String verifiedBy,
                                     List<UUID> verifiedRepresentativeIds) throws Exception {
        OrganizationEntity org = get(tenantId, organizationId);
        if (org.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("organization is not pending verification: "
                    + org.getVerificationStatus());
        }
        if (verifiedBy == null || verifiedBy.isBlank()) {
            throw new IllegalArgumentException("verifiedBy (national admin actor) is required");
        }
        if (verifiedRepresentativeIds != null) {
            for (UUID repId : verifiedRepresentativeIds) {
                AuthorizedRepresentativeEntity rep = representativeRepository
                        .findByOrganizationIdAndId(org.getId(), repId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "representative does not belong to organization: " + repId));
                rep.setVerificationStatus(VerificationStatus.VERIFIED);
                representativeRepository.save(rep);
            }
        }
        if (!representativeRepository.existsByOrganizationIdAndVerificationStatus(
                org.getId(), VerificationStatus.VERIFIED)) {
            throw new IllegalStateException(
                    "organization verification requires at least one VERIFIED authorized representative");
        }
        org.setVerificationStatus(VerificationStatus.VERIFIED);
        org.setStatus("ACTIVE");
        org.setVerifiedBy(verifiedBy);
        org.setVerifiedAt(OffsetDateTime.now());
        OrganizationEntity saved = organizationRepository.save(org);
        outboxWriter.publish(tenantId, "ORGANIZATION", saved.getId().toString(),
                "organization", "verified",
                "org-registry:organization:verified:" + saved.getId(),
                Map.of(
                        "organizationId", saved.getId().toString(),
                        "verifiedBy", verifiedBy));
        return saved;
    }
}
