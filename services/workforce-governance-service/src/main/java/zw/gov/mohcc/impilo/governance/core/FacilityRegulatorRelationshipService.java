package zw.gov.mohcc.impilo.governance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.FacilityRegulatorRelationshipEntity;
import zw.gov.mohcc.impilo.governance.persistence.FacilityRegulatorRelationshipRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the multi-regulator facility&lt;-&gt;council relationship (WGV SoR). A facility
 * may be regulated by MANY councils; there is no hardcoded single regulator.
 *
 * <p>Mutations emit governance outbox events. Authz via existing ext_authz; policies
 * {@code ORG-ADMIN}, {@code REGULATOR-MODE} (spec-only, track P).</p>
 */
@Service
public class FacilityRegulatorRelationshipService {

    private static final Logger log = LoggerFactory.getLogger(FacilityRegulatorRelationshipService.class);

    private final FacilityRegulatorRelationshipRepository repository;
    private final GovernanceEventService eventService;

    public FacilityRegulatorRelationshipService(FacilityRegulatorRelationshipRepository repository,
                                                GovernanceEventService eventService) {
        this.repository = repository;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<FacilityRegulatorRelationshipEntity> listForFacility(UUID tenantId, String facilityId) {
        return repository.findByTenantIdAndFacilityIdOrderByCreatedAtAsc(tenantId, facilityId);
    }

    @Transactional(readOnly = true)
    public List<FacilityRegulatorRelationshipEntity> listForCouncil(UUID tenantId, String councilId) {
        return repository.findByTenantIdAndCouncilIdOrderByCreatedAtAsc(tenantId, councilId);
    }

    @Transactional
    public FacilityRegulatorRelationshipEntity link(UUID tenantId, String actor, String facilityId,
                                                    Map<String, Object> req, String correlationId) {
        String councilId = str(req.get("councilId"));
        String councilCode = str(req.get("councilCode"));
        String relationshipType = strOrDefault(req.get("relationshipType"), "PRIMARY_REGULATOR");
        if (councilId == null || councilCode == null) {
            throw new IllegalArgumentException("councilId and councilCode are required");
        }

        FacilityRegulatorRelationshipEntity entity = repository
                .findByTenantIdAndFacilityIdAndCouncilIdAndRelationshipType(
                        tenantId, facilityId, councilId, relationshipType)
                .orElseGet(FacilityRegulatorRelationshipEntity::new);

        entity.setTenantId(tenantId);
        entity.setFacilityId(facilityId);
        entity.setCouncilId(councilId);
        entity.setCouncilCode(councilCode);
        entity.setRelationshipType(relationshipType);
        entity.setRegistrationNumber(str(req.get("registrationNumber")));
        entity.setStatus(strOrDefault(req.get("status"), "ACTIVE"));
        entity.setValidFrom(date(req.get("validFrom")));
        entity.setValidTo(date(req.get("validTo")));
        if (entity.getCreatedBy() == null) entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        FacilityRegulatorRelationshipEntity saved = repository.save(entity);

        eventService.enqueue("FACILITY_REGULATOR_RELATIONSHIP", saved.getId().toString(),
                "impilo.governance.facility.regulator.linked",
                Map.of("facilityId", facilityId, "councilId", councilId,
                        "councilCode", councilCode, "relationshipType", relationshipType),
                tenantId, correlationId);
        log.info("Facility {} linked to council {} ({}) tenant {}", facilityId, councilCode, relationshipType, tenantId);
        return saved;
    }

    @Transactional
    public FacilityRegulatorRelationshipEntity updateStatus(UUID tenantId, String actor, UUID id,
                                                            String status, String correlationId) {
        FacilityRegulatorRelationshipEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Relationship not found: " + id));
        entity.setStatus(status);
        entity.setUpdatedBy(actor);
        FacilityRegulatorRelationshipEntity saved = repository.save(entity);
        eventService.enqueue("FACILITY_REGULATOR_RELATIONSHIP", id.toString(),
                "impilo.governance.facility.regulator.status.changed",
                Map.of("relationshipId", id.toString(), "status", status), tenantId, correlationId);
        return saved;
    }

    @Transactional
    public void unlink(UUID tenantId, String actor, UUID id, String correlationId) {
        FacilityRegulatorRelationshipEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Relationship not found: " + id));
        entity.setStatus("REVOKED");
        entity.setUpdatedBy(actor);
        repository.save(entity);
        eventService.enqueue("FACILITY_REGULATOR_RELATIONSHIP", id.toString(),
                "impilo.governance.facility.regulator.unlinked",
                Map.of("relationshipId", id.toString()), tenantId, correlationId);
    }

    private static String str(Object o) { return o != null ? o.toString() : null; }
    private static String strOrDefault(Object o, String def) {
        String s = str(o);
        return s != null && !s.isBlank() ? s : def;
    }
    private static LocalDate date(Object o) {
        String s = str(o);
        return s != null && !s.isBlank() ? LocalDate.parse(s) : null;
    }
}
