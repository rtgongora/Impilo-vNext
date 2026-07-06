package zw.gov.mohcc.impilo.governance.core;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorEvent;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorPayload;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final GovernanceEventService governanceEventService;
    private final ApplicationEventPublisher eventPublisher;

    public OrganisationService(OrganisationRepository organisationRepository,
                               GovernanceEventService governanceEventService,
                               ApplicationEventPublisher eventPublisher) {
        this.organisationRepository = organisationRepository;
        this.governanceEventService = governanceEventService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Enqueue a one-way mirror push for {@code org}. Published as an in-process
     * event consumed AFTER_COMMIT by the mirror relay, so the mirror call is
     * fully decoupled from — and can never roll back — this primary write.
     */
    private void enqueueMirror(OrganisationEntity org, TrustContext ctx, String changeType) {
        eventPublisher.publishEvent(new OrgMirrorEvent(
                OrgMirrorPayload.from(org),
                org.getTenantId(),
                ctx != null ? ctx.correlationId() : null,
                changeType));
    }

    @Transactional
    public OrganisationEntity createOrganisation(UUID tenantId,
                                                 String organisationCode,
                                                 String name,
                                                 String legalName,
                                                 String organisationType,
                                                 UUID parentOrganisationId,
                                                 String metadataJson) {
        TrustContext ctx = TrustContextHolder.require();
        if (organisationRepository.findByTenantIdAndOrganisationCode(tenantId, organisationCode).isPresent()) {
            throw new IllegalArgumentException("Organisation code already exists for tenant");
        }
        OrganisationEntity org = new OrganisationEntity(
                UUID.randomUUID(),
                tenantId,
                organisationCode,
                name,
                organisationType,
                GovernanceEnums.OrganisationStatus.DRAFT.name());
        org.setLegalName(legalName);
        org.setParentOrganisationId(parentOrganisationId);
        org.setMetadata(metadataJson);
        org = organisationRepository.save(org);
        governanceEventService.enqueue("ORGANISATION", org.getId().toString(), "impilo.governance.organisation.created",
                Map.of("organisationId", org.getId().toString(), "code", organisationCode, "status", org.getStatus()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        enqueueMirror(org, ctx, "created");
        return org;
    }

    @Transactional
    public OrganisationEntity updateStatus(UUID organisationId, GovernanceEnums.OrganisationStatus newStatus) {
        TrustContext ctx = TrustContextHolder.require();
        OrganisationEntity org = organisationRepository.findById(organisationId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        org.setStatus(newStatus.name());
        if (newStatus == GovernanceEnums.OrganisationStatus.ACTIVE) {
            org.setActiveFlag(true);
        } else if (newStatus == GovernanceEnums.OrganisationStatus.CLOSED
                || newStatus == GovernanceEnums.OrganisationStatus.INACTIVE) {
            org.setActiveFlag(false);
        }
        org = organisationRepository.save(org);
        governanceEventService.enqueue("ORGANISATION", org.getId().toString(), "impilo.governance.organisation.updated",
                Map.of("organisationId", org.getId().toString(), "status", org.getStatus()),
                org.getTenantId(), ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        enqueueMirror(org, ctx, "updated");
        return org;
    }

    public OrganisationEntity get(UUID id) {
        return organisationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
    }

    public List<OrganisationEntity> list(UUID tenantId) {
        return organisationRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional
    public OrganisationEntity patchOrganisation(UUID organisationId, String name, String legalName, String metadataJson) {
        TrustContext ctx = TrustContextHolder.require();
        OrganisationEntity org = get(organisationId);
        if (name != null && !name.isBlank()) org.setName(name);
        if (legalName != null) org.setLegalName(legalName);
        if (metadataJson != null) org.setMetadata(metadataJson);
        org = organisationRepository.save(org);
        governanceEventService.enqueue("ORGANISATION", org.getId().toString(), "impilo.governance.organisation.patched",
                Map.of("organisationId", org.getId().toString()), org.getTenantId(),
                ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        enqueueMirror(org, ctx, "patched");
        return org;
    }
}
