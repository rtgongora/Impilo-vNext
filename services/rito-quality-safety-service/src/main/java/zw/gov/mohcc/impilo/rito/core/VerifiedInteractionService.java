package zw.gov.mohcc.impilo.rito.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.persistence.entity.VerifiedInteractionEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.VerifiedInteractionRepository;

import java.time.OffsetDateTime;
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

    private final VerifiedInteractionRepository repository;

    public VerifiedInteractionService(VerifiedInteractionRepository repository) {
        this.repository = repository;
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
            VerifiedInteractionEntity saved = repository.save(e);
            log.info("Recorded verified interaction encounter={} provider={} facility={}",
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
