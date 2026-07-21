package zw.gov.mohcc.impilo.rito.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.VerifiedInteractionEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.VerifiedInteractionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records a completed PCT encounter as an eligible-for-feedback interaction — the
 * verification gate for the Experience & Reputation capability (RW2). A later rating
 * (RW3) that references the same {@code encounterRef} is stamped verified. Idempotent
 * per (tenant, encounterRef): re-consuming the same ENCOUNTER_COMPLETED event is a no-op.
 */
@Service
public class VerifiedInteractionService {

    private static final Logger log = LoggerFactory.getLogger(VerifiedInteractionService.class);

    /** Notification-service template a downstream deliverer (Khuluma) uses for the request. */
    private static final String FEEDBACK_TEMPLATE_KEY = "POST_VISIT_FEEDBACK_REQUEST";

    private final VerifiedInteractionRepository repository;
    private final RitoEventEmitter emitter;

    public VerifiedInteractionService(VerifiedInteractionRepository repository, RitoEventEmitter emitter) {
        this.repository = repository;
        this.emitter = emitter;
    }

    /**
     * Record (idempotently) a verified interaction from a PCT {@code ENCOUNTER_COMPLETED}
     * payload. Returns the persisted or pre-existing record, or {@code null} when the
     * payload lacks a usable encounter reference.
     */
    @Transactional
    public VerifiedInteractionEntity recordFromPct(UUID tenantId, Map<String, Object> payload) {
        String encounterRef = str(payload, "encounterRef");
        if (tenantId == null || encounterRef == null) {
            log.warn("Skipping verified-interaction intake — missing tenantId or encounterRef");
            return null;
        }
        return repository.findByTenantIdAndEncounterRef(tenantId, encounterRef).orElseGet(() -> {
            VerifiedInteractionEntity e = new VerifiedInteractionEntity();
            e.setTenantId(tenantId);
            e.setEncounterRef(encounterRef);
            e.setAttendingProviderId(str(payload, "attendingProviderId"));
            e.setPatientCpid(str(payload, "patientCpid"));
            e.setFacilityId(uuid(payload, "facilityId"));
            e.setEncounterType(str(payload, "encounterType"));
            e.setModality(str(payload, "modality"));
            e.setEndedAt(offsetDateTime(payload, "endedAt"));
            e.setSourceSystem("PCT");
            e.setFeedbackRequested(true);
            VerifiedInteractionEntity saved = repository.save(e);

            // Rito owns the "request feedback" decision; Khuluma owns delivery and
            // notification-service owns recipient/comm-preference resolution. Emit the
            // request signal carrying the encounter context + the template key.
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("tenantId", tenantId.toString());
            event.put("encounterRef", saved.getEncounterRef());
            event.put("attendingProviderId", saved.getAttendingProviderId());
            event.put("patientCpid", saved.getPatientCpid());
            event.put("facilityId", saved.getFacilityId() != null ? saved.getFacilityId().toString() : null);
            event.put("encounterType", saved.getEncounterType());
            event.put("templateKey", FEEDBACK_TEMPLATE_KEY);
            event.put("messageKind", "POST_VISIT_FEEDBACK");
            emitter.emit("FEEDBACK_REQUEST", saved.getId().toString(), "rito.feedback.requested",
                    "ENCOUNTER", saved.getEncounterRef(), event, tenantId);

            log.info("Recorded verified interaction encounter={} provider={} facility={} (feedback requested)",
                    encounterRef, saved.getAttendingProviderId(), saved.getFacilityId());
            return saved;
        });
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v == null || v.toString().isBlank()) ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> m, String key) {
        String s = str(m, key);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static OffsetDateTime offsetDateTime(Map<String, Object> m, String key) {
        String s = str(m, key);
        if (s == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
