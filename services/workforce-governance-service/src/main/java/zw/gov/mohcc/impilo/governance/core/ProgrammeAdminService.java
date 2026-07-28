package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administers the canonical health-programme registry (WGV SoR). Programme
 * appointments are wgv_assignment rows (target_type=PROGRAM); this service
 * owns only the programme identity and its jurisdiction/facility/service
 * scope declarations.
 */
@Service
public class ProgrammeAdminService {

    private final ProgrammeRepository programmeRepository;
    private final ProgrammeJurisdictionRepository jurisdictionRepository;
    private final ProgrammeFacilityRepository facilityRepository;
    private final ProgrammeServiceRepository serviceRepository;
    private final ProgrammeSourceReferenceRepository sourceReferenceRepository;
    private final JurisdictionRepository wgvJurisdictionRepository;
    private final GovernanceEventService governanceEventService;

    public ProgrammeAdminService(ProgrammeRepository programmeRepository,
                                  ProgrammeJurisdictionRepository jurisdictionRepository,
                                  ProgrammeFacilityRepository facilityRepository,
                                  ProgrammeServiceRepository serviceRepository,
                                  ProgrammeSourceReferenceRepository sourceReferenceRepository,
                                  JurisdictionRepository wgvJurisdictionRepository,
                                  GovernanceEventService governanceEventService) {
        this.programmeRepository = programmeRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.facilityRepository = facilityRepository;
        this.serviceRepository = serviceRepository;
        this.sourceReferenceRepository = sourceReferenceRepository;
        this.wgvJurisdictionRepository = wgvJurisdictionRepository;
        this.governanceEventService = governanceEventService;
    }

    @Transactional
    public ProgrammeEntity createProgramme(UUID tenantId, String programmeCode, String name, String programmeType,
                                            String scopeLevel, UUID parentProgrammeId, UUID responsibleOrganisationId,
                                            String accountableOffice, LocalDate validFrom, LocalDate validTo,
                                            String description) {
        TrustContext ctx = TrustContextHolder.require();
        if (programmeRepository.findByTenantIdAndProgrammeCode(tenantId, programmeCode).isPresent()) {
            throw new IllegalArgumentException("Programme code already exists: " + programmeCode);
        }
        ProgrammeEntity p = new ProgrammeEntity(UUID.randomUUID(), tenantId, programmeCode, name,
                programmeType, "ACTIVE", scopeLevel);
        p.setParentProgrammeId(parentProgrammeId);
        p.setResponsibleOrganisationId(responsibleOrganisationId);
        p.setAccountableOffice(accountableOffice);
        p.setValidFrom(validFrom);
        p.setValidTo(validTo);
        p.setDescription(description);
        p.setCreatedBy(ctx.actorId());
        p.setUpdatedBy(ctx.actorId());
        p = programmeRepository.save(p);
        governanceEventService.enqueue("PROGRAMME", p.getId().toString(), "impilo.governance.programme.created",
                Map.of("programmeId", p.getId().toString(), "code", programmeCode, "programmeType", programmeType),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return p;
    }

    @Transactional
    public ProgrammeEntity transition(UUID programmeId, String newStatus) {
        TrustContext ctx = TrustContextHolder.require();
        ProgrammeEntity p = require(programmeId);
        String previous = p.getStatus();
        if (!List.of("ACTIVE", "SUSPENDED", "CLOSED", "ARCHIVED").contains(newStatus)) {
            throw new IllegalArgumentException("Unknown programme status: " + newStatus);
        }
        p.setStatus(newStatus);
        p.setUpdatedBy(ctx.actorId());
        p = programmeRepository.save(p);
        governanceEventService.enqueue("PROGRAMME", p.getId().toString(), "impilo.governance.programme.status_changed",
                Map.of("programmeId", p.getId().toString(), "previousStatus", String.valueOf(previous), "newStatus", newStatus),
                p.getTenantId(), ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return p;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeEntity> list(UUID tenantId, String status) {
        return status == null || status.isBlank()
                ? programmeRepository.findByTenantIdOrderByNameAsc(tenantId)
                : programmeRepository.findByTenantIdAndStatusOrderByNameAsc(tenantId, status);
    }

    @Transactional(readOnly = true)
    public ProgrammeEntity get(UUID programmeId) {
        return require(programmeId);
    }

    @Transactional
    public ProgrammeJurisdictionEntity linkJurisdiction(UUID tenantId, UUID programmeId, UUID jurisdictionId,
                                                          LocalDate startDate, LocalDate endDate) {
        TrustContext ctx = TrustContextHolder.require();
        require(programmeId);
        if (wgvJurisdictionRepository.findById(jurisdictionId).isEmpty()) {
            throw new IllegalArgumentException("Jurisdiction not found: " + jurisdictionId);
        }
        ProgrammeJurisdictionEntity link = new ProgrammeJurisdictionEntity(UUID.randomUUID(), tenantId, programmeId, jurisdictionId);
        link.setStartDate(startDate);
        link.setEndDate(endDate);
        link = jurisdictionRepository.save(link);
        governanceEventService.enqueue("PROGRAMME_JURISDICTION", link.getId().toString(),
                "impilo.governance.programme.jurisdiction_linked",
                Map.of("programmeId", programmeId.toString(), "jurisdictionId", jurisdictionId.toString()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return link;
    }

    @Transactional
    public ProgrammeFacilityEntity linkFacility(UUID tenantId, UUID programmeId, String facilityId,
                                                 LocalDate startDate, LocalDate endDate) {
        TrustContext ctx = TrustContextHolder.require();
        require(programmeId);
        ProgrammeFacilityEntity link = new ProgrammeFacilityEntity(UUID.randomUUID(), tenantId, programmeId, facilityId);
        link.setStartDate(startDate);
        link.setEndDate(endDate);
        link = facilityRepository.save(link);
        governanceEventService.enqueue("PROGRAMME_FACILITY", link.getId().toString(),
                "impilo.governance.programme.facility_linked",
                Map.of("programmeId", programmeId.toString(), "facilityId", facilityId),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return link;
    }

    @Transactional
    public ProgrammeServiceEntity addService(UUID tenantId, UUID programmeId, String serviceCode, String serviceLabel) {
        TrustContext ctx = TrustContextHolder.require();
        require(programmeId);
        ProgrammeServiceEntity svc = new ProgrammeServiceEntity(UUID.randomUUID(), tenantId, programmeId, serviceCode, serviceLabel);
        svc = serviceRepository.save(svc);
        governanceEventService.enqueue("PROGRAMME_SERVICE", svc.getId().toString(),
                "impilo.governance.programme.service_added",
                Map.of("programmeId", programmeId.toString(), "serviceCode", serviceCode),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return svc;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeJurisdictionEntity> jurisdictions(UUID programmeId) {
        return jurisdictionRepository.findByProgrammeIdAndStatus(programmeId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<ProgrammeFacilityEntity> facilities(UUID programmeId) {
        return facilityRepository.findByProgrammeIdAndStatus(programmeId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<ProgrammeServiceEntity> services(UUID programmeId) {
        return serviceRepository.findByProgrammeId(programmeId);
    }

    /**
     * Records provenance for a programme resolved from a legacy free-text
     * programme_id during the A5 migration/quarantine sweep. Idempotent per
     * (sourceService, sourceValue).
     */
    @Transactional
    public ProgrammeSourceReferenceEntity recordSourceReference(UUID tenantId, UUID programmeId,
                                                                  String sourceService, String sourceValue) {
        TrustContext ctx = TrustContextHolder.require();
        return sourceReferenceRepository.findBySourceServiceAndSourceValue(sourceService, sourceValue)
                .orElseGet(() -> {
                    ProgrammeEntity p = require(programmeId);
                    if (!"NATIVE".equals(p.getProvenance())) {
                        // already migrated once; leave as-is
                    } else {
                        p.setProvenance("MIGRATED");
                        programmeRepository.save(p);
                    }
                    return sourceReferenceRepository.save(new ProgrammeSourceReferenceEntity(
                            UUID.randomUUID(), tenantId, programmeId, sourceService, sourceValue, ctx.actorId()));
                });
    }

    private ProgrammeEntity require(UUID programmeId) {
        return programmeRepository.findById(programmeId)
                .orElseThrow(() -> new IllegalArgumentException("Programme not found: " + programmeId));
    }
}
