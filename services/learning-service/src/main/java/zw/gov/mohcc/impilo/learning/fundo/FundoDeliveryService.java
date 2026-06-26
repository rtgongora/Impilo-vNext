package zw.gov.mohcc.impilo.learning.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CohortFacilitatorEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FacilitatorEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.TrainingVenueEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CohortFacilitatorRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FacilitatorRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.TrainingVenueRepository;

/**
 * Learning-delivery administration (A1): facilitators, training venues, the
 * cohort↔facilitator link, and structured assignment of facilitator/venue to a
 * scheduled session. Backs the studio delivery surfaces.
 */
@Service
public class FundoDeliveryService {

    private final FacilitatorRepository facilitatorRepository;
    private final TrainingVenueRepository venueRepository;
    private final CohortFacilitatorRepository cohortFacilitatorRepository;
    private final ScheduledLearningSessionRepository sessionRepository;

    public FundoDeliveryService(
            FacilitatorRepository facilitatorRepository,
            TrainingVenueRepository venueRepository,
            CohortFacilitatorRepository cohortFacilitatorRepository,
            ScheduledLearningSessionRepository sessionRepository) {
        this.facilitatorRepository = facilitatorRepository;
        this.venueRepository = venueRepository;
        this.cohortFacilitatorRepository = cohortFacilitatorRepository;
        this.sessionRepository = sessionRepository;
    }

    // ---- Facilitators ----

    @Transactional
    public Map<String, Object> createFacilitator(UUID tenantId, String actorId, Map<String, Object> body) {
        FacilitatorEntity f = new FacilitatorEntity();
        f.setTenantId(tenantId);
        f.setDisplayName(reqStr(body, "displayName"));
        f.setFacilitatorKind(str(body, "facilitatorKind", "TRAINER"));
        f.setSubjectType(nullable(body, "subjectType"));
        f.setSubjectId(nullable(body, "subjectId"));
        f.setCadre(nullable(body, "cadre"));
        f.setEmail(nullable(body, "email"));
        f.setStatus(str(body, "status", "ACTIVE"));
        f.setCreatedBy(actorId);
        facilitatorRepository.save(f);
        return facilitatorView(f);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listFacilitators(UUID tenantId, String kind, int limit) {
        List<FacilitatorEntity> rows = (kind == null || kind.isBlank())
                ? facilitatorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit))
                : facilitatorRepository.findByTenantIdAndFacilitatorKindOrderByCreatedAtDesc(
                        tenantId, kind, PageRequest.of(0, limit));
        return Map.of("items", rows.stream().map(FundoDeliveryService::facilitatorView).toList(), "limit", limit);
    }

    @Transactional
    public Map<String, Object> updateFacilitatorStatus(UUID tenantId, String facilitatorId, String status) {
        FacilitatorEntity f = facilitatorRepository.findByTenantIdAndId(tenantId, UUID.fromString(facilitatorId))
                .orElseThrow(() -> new IllegalArgumentException("facilitator not found"));
        f.setStatus(status);
        facilitatorRepository.save(f);
        return facilitatorView(f);
    }

    // ---- Venues ----

    @Transactional
    public Map<String, Object> createVenue(UUID tenantId, String actorId, Map<String, Object> body) {
        TrainingVenueEntity v = new TrainingVenueEntity();
        v.setTenantId(tenantId);
        v.setName(reqStr(body, "name"));
        v.setVenueKind(str(body, "venueKind", "PHYSICAL"));
        v.setFacilityLevel(nullable(body, "facilityLevel"));
        v.setLocationRef(nullable(body, "locationRef"));
        Object cap = body.get("capacity");
        if (cap != null) {
            v.setCapacity(Integer.valueOf(cap.toString()));
        }
        v.setStatus(str(body, "status", "ACTIVE"));
        v.setCreatedBy(actorId);
        venueRepository.save(v);
        return venueView(v);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listVenues(UUID tenantId, int limit) {
        List<TrainingVenueEntity> rows = venueRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(0, limit));
        return Map.of("items", rows.stream().map(FundoDeliveryService::venueView).toList(), "limit", limit);
    }

    // ---- Cohort ↔ facilitator ----

    @Transactional
    public Map<String, Object> assignCohortFacilitator(UUID tenantId, String cohortId, String actorId, Map<String, Object> body) {
        CohortFacilitatorEntity link = new CohortFacilitatorEntity();
        link.setTenantId(tenantId);
        link.setCohortId(UUID.fromString(cohortId));
        link.setFacilitatorId(UUID.fromString(reqStr(body, "facilitatorId")));
        link.setRoleInCohort(str(body, "roleInCohort", "LEAD"));
        link.setAssignedBy(actorId);
        cohortFacilitatorRepository.save(link);
        return Map.of("id", link.getId().toString(), "cohortId", cohortId,
                "facilitatorId", link.getFacilitatorId().toString(), "roleInCohort", link.getRoleInCohort());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCohortFacilitators(UUID tenantId, String cohortId) {
        List<Map<String, Object>> items = cohortFacilitatorRepository
                .findByTenantIdAndCohortId(tenantId, UUID.fromString(cohortId)).stream()
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", l.getId().toString());
                    m.put("facilitatorId", l.getFacilitatorId().toString());
                    m.put("roleInCohort", l.getRoleInCohort());
                    facilitatorRepository.findByTenantIdAndId(tenantId, l.getFacilitatorId())
                            .ifPresent(f -> m.put("facilitator", facilitatorView(f)));
                    return m;
                }).toList();
        return Map.of("items", items);
    }

    // ---- Session structured delivery assignment ----

    @Transactional
    public Map<String, Object> assignSessionDelivery(UUID tenantId, String sessionId, Map<String, Object> body) {
        ScheduledLearningSessionEntity s = sessionRepository.findByTenantIdAndId(tenantId, UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        if (body.containsKey("facilitatorId")) {
            s.setFacilitatorId(uuidOrNull(body.get("facilitatorId")));
        }
        if (body.containsKey("venueId")) {
            s.setVenueId(uuidOrNull(body.get("venueId")));
        }
        sessionRepository.save(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("facilitatorId", s.getFacilitatorId() == null ? null : s.getFacilitatorId().toString());
        m.put("venueId", s.getVenueId() == null ? null : s.getVenueId().toString());
        // Read-preference: resolved display falls back to the legacy free-text columns.
        m.put("facilitatorDisplay", resolveFacilitatorDisplay(tenantId, s));
        m.put("venueDisplay", resolveVenueDisplay(tenantId, s));
        return m;
    }

    private String resolveFacilitatorDisplay(UUID tenantId, ScheduledLearningSessionEntity s) {
        if (s.getFacilitatorId() != null) {
            return facilitatorRepository.findByTenantIdAndId(tenantId, s.getFacilitatorId())
                    .map(FacilitatorEntity::getDisplayName).orElse(s.getFacilitator());
        }
        return s.getFacilitator();
    }

    private String resolveVenueDisplay(UUID tenantId, ScheduledLearningSessionEntity s) {
        if (s.getVenueId() != null) {
            return venueRepository.findByTenantIdAndId(tenantId, s.getVenueId())
                    .map(TrainingVenueEntity::getName).orElse(s.getLocationRef());
        }
        return s.getLocationRef();
    }

    // ---- views / helpers ----

    private static Map<String, Object> facilitatorView(FacilitatorEntity f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId().toString());
        m.put("displayName", f.getDisplayName());
        m.put("facilitatorKind", f.getFacilitatorKind());
        m.put("cadre", f.getCadre());
        m.put("email", f.getEmail());
        m.put("subjectType", f.getSubjectType());
        m.put("subjectId", f.getSubjectId());
        m.put("status", f.getStatus());
        return m;
    }

    private static Map<String, Object> venueView(TrainingVenueEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId().toString());
        m.put("name", v.getName());
        m.put("venueKind", v.getVenueKind());
        m.put("facilityLevel", v.getFacilityLevel());
        m.put("locationRef", v.getLocationRef());
        m.put("capacity", v.getCapacity());
        m.put("status", v.getStatus());
        return m;
    }

    private static UUID uuidOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return UUID.fromString(o.toString());
    }

    private static String reqStr(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? def : v.toString();
    }

    private static String nullable(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }
}
