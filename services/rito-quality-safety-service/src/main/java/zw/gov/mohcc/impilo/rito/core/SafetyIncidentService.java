package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.SafetyIncidentEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.SafetyIncidentRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Manages the 1:1 {@code rit_safety_incident} detail extension attached to a safety-type
 * case. The owning case aggregate lives in {@link CaseService}; this service only enriches
 * a case with incident specifics (harm level, contributing factors, RCA) and raises the
 * sentinel critical-alert path when a sentinel event is recorded.
 */
@Service
public class SafetyIncidentService {

    private final SafetyIncidentRepository repository;
    private final CaseRepository caseRepository;
    private final RitoEventEmitter emitter;
    private final NotificationGateway notifications;

    public SafetyIncidentService(SafetyIncidentRepository repository, CaseRepository caseRepository,
                                 RitoEventEmitter emitter, NotificationGateway notifications) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.emitter = emitter;
        this.notifications = notifications;
    }

    @Transactional
    public SafetyIncidentEntity recordIncident(UUID tenantId, UUID caseId, String incidentType,
                                               String harmLevel, OffsetDateTime occurredAt,
                                               String locationDetail, Map<String, Object> contributingFactors,
                                               String immediateActions, Boolean sentinel,
                                               String actorId, String actorType) {
        CaseEntity c = caseRepository.findByIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Case not found: " + caseId));

        SafetyIncidentEntity incident = repository.findByCaseId(caseId).orElseGet(() -> {
            SafetyIncidentEntity e = new SafetyIncidentEntity();
            e.setCaseId(c.getId());
            e.setTenantId(tenantId);
            return e;
        });

        incident.setIncidentType(incidentType);
        incident.setHarmLevel(harmLevel != null ? harmLevel : "NONE");
        incident.setOccurredAt(occurredAt);
        incident.setLocationDetail(locationDetail);
        incident.setContributingFactorsJson(JsonUtil.toJson(contributingFactors));
        incident.setImmediateActions(immediateActions);
        incident.setSentinel(Boolean.TRUE.equals(sentinel));
        repository.save(incident);

        if (Boolean.TRUE.equals(sentinel)) {
            emitter.emit("SAFETY_INCIDENT", incident.getId().toString(),
                    "rito.safety.critical_alert.created", "CASE", caseId.toString(),
                    Map.of("caseId", caseId.toString(), "incidentType", incidentType,
                            "harmLevel", incident.getHarmLevel(), "sentinel", Boolean.TRUE),
                    tenantId);
            notifications.requestNotification(tenantId, caseId, "rito.safety.critical_alert",
                    "FACILITY_SAFETY_LEAD", Map.of("incidentType", incidentType));
        }
        return incident;
    }

    @Transactional
    public SafetyIncidentEntity updateRca(UUID tenantId, UUID caseId, String rcaMethod, String rcaSummary) {
        SafetyIncidentEntity incident = load(tenantId, caseId);
        incident.setRcaMethod(rcaMethod);
        incident.setRcaSummary(rcaSummary);
        return repository.save(incident);
    }

    @Transactional(readOnly = true)
    public SafetyIncidentEntity get(UUID tenantId, UUID caseId) {
        return load(tenantId, caseId);
    }

    @Transactional(readOnly = true)
    public List<SafetyIncidentEntity> listSentinel(UUID tenantId) {
        return repository.findByTenantIdAndSentinelTrue(tenantId);
    }

    // ---- internal helpers ----

    private SafetyIncidentEntity load(UUID tenantId, UUID caseId) {
        SafetyIncidentEntity incident = repository.findByCaseId(caseId)
                .orElseThrow(() -> new NoSuchElementException("Safety incident not found for case: " + caseId));
        if (!incident.getTenantId().equals(tenantId)) {
            throw new NoSuchElementException("Safety incident not found for case: " + caseId);
        }
        return incident;
    }
}
