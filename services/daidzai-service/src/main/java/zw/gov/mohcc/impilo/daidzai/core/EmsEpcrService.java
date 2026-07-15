package zw.gov.mohcc.impilo.daidzai.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.integration.PctPreArrivalClient;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsEpcrEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsEpcrEventEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsMissionEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmsEpcrEventRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmsEpcrRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmsMissionRepository;

import java.util.*;

/**
 * Prehospital ePCR capture (architecture decision #2 / W3). The responder crew captures a primary
 * survey and a stream of timed observations (vitals / GCS / interventions) on the mobile
 * provider-app; they persist here as {@code dai_ems_epcr} + {@code dai_ems_epcr_event}. On each
 * observation a compact snapshot is pushed to PCT's ED pre-arrival projection so the destination ED
 * sees the incoming patient BEFORE the ambulance arrives. DAIDZAI owns the ePCR; PCT owns the ED
 * projection; obs never live in nhume/dispatch.
 */
@Service
public class EmsEpcrService {

    private static final Logger log = LoggerFactory.getLogger(EmsEpcrService.class);

    private final EmsMissionRepository missionRepo;
    private final EmsEpcrRepository epcrRepo;
    private final EmsEpcrEventRepository eventRepo;
    private final DaidzaiEventEmitter emitter;
    private final PctPreArrivalClient preArrival;
    private final ObjectMapper mapper;

    public EmsEpcrService(EmsMissionRepository missionRepo, EmsEpcrRepository epcrRepo,
                          EmsEpcrEventRepository eventRepo, DaidzaiEventEmitter emitter,
                          PctPreArrivalClient preArrival, ObjectMapper mapper) {
        this.missionRepo = missionRepo;
        this.epcrRepo = epcrRepo;
        this.eventRepo = eventRepo;
        this.emitter = emitter;
        this.preArrival = preArrival;
        this.mapper = mapper;
    }

    /** Create or update the ePCR for a mission (one per mission). Binds the patient + primary survey. */
    @Transactional
    public EmsEpcrEntity upsertEpcr(UUID tenantId, UUID missionId, String patientHealthId,
                                    String primarySurveyJson, String narrative) {
        EmsMissionEntity m = requireMission(tenantId, missionId);
        EmsEpcrEntity epcr = epcrRepo.findByTenantIdAndMissionId(tenantId, missionId)
                .orElseGet(EmsEpcrEntity::new);
        epcr.setTenantId(tenantId);
        epcr.setMissionId(missionId);
        epcr.setTraumaEpisodeId(m.getTraumaEpisodeId());
        if (patientHealthId != null) epcr.setPatientHealthId(patientHealthId);
        if (primarySurveyJson != null) epcr.setPrimarySurveyJson(primarySurveyJson);
        if (narrative != null) epcr.setNarrative(narrative);
        epcr = epcrRepo.save(epcr);
        emitter.emit("EMS_EPCR", epcr.getId().toString(), "daidzai.ems.epcr_recorded",
                "TRAUMA_EPISODE", subjectId(m), Map.of("missionId", missionId.toString(),
                        "patientHealthId", patientHealthId == null ? "" : patientHealthId), tenantId);
        pushPreArrival(m, epcr);
        return epcr;
    }

    /**
     * Append a timed observation (VITALS / GCS / INTERVENTION / MEDICATION / NOTE) and refresh the
     * ED pre-arrival projection. Auto-creates the ePCR shell if the crew logs an obs before the
     * primary survey.
     */
    @Transactional
    public EmsEpcrEventEntity recordEvent(UUID tenantId, UUID missionId, String eventType,
                                          String channel, String payloadJson) {
        EmsMissionEntity m = requireMission(tenantId, missionId);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        EmsEpcrEntity epcr = epcrRepo.findByTenantIdAndMissionId(tenantId, missionId)
                .orElseGet(() -> upsertEpcr(tenantId, missionId, null, null, null));

        EmsEpcrEventEntity ev = new EmsEpcrEventEntity();
        ev.setTenantId(tenantId);
        ev.setEpcrId(epcr.getId());
        ev.setMissionId(missionId);
        ev.setEventType(eventType.toUpperCase());
        ev.setChannel(channel);
        ev.setPayloadJson(payloadJson);
        ev = eventRepo.save(ev);

        emitter.emit("EMS_EPCR", epcr.getId().toString(), "daidzai.ems.epcr_event",
                "TRAUMA_EPISODE", subjectId(m),
                Map.of("missionId", missionId.toString(), "eventType", ev.getEventType()), tenantId);
        pushPreArrival(m, epcr);
        return ev;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEpcr(UUID tenantId, UUID missionId) {
        EmsEpcrEntity epcr = epcrRepo.findByTenantIdAndMissionId(tenantId, missionId)
                .orElseThrow(() -> new NoSuchElementException("No ePCR for mission: " + missionId));
        List<EmsEpcrEventEntity> events = eventRepo.findByEpcrIdOrderByRecordedAtAsc(epcr.getId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("epcrId", epcr.getId().toString());
        view.put("missionId", missionId.toString());
        view.put("traumaEpisodeId", epcr.getTraumaEpisodeId() != null ? epcr.getTraumaEpisodeId().toString() : null);
        view.put("patientHealthId", epcr.getPatientHealthId());
        view.put("narrative", epcr.getNarrative());
        view.put("events", events.stream().map(this::eventView).toList());
        view.put("snapshot", buildSnapshot(epcr, events));
        return view;
    }

    // ── internals ──────────────────────────────────────────────────────────

    private void pushPreArrival(EmsMissionEntity m, EmsEpcrEntity epcr) {
        if (m.getTraumaEpisodeId() == null && m.getDestinationFacilityId() == null) {
            return; // nothing for the ED to key on yet
        }
        List<EmsEpcrEventEntity> events = eventRepo.findByEpcrIdOrderByRecordedAtAsc(epcr.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traumaEpisodeId", m.getTraumaEpisodeId() != null ? m.getTraumaEpisodeId().toString() : null);
        body.put("facilityId", m.getDestinationFacilityId() != null ? m.getDestinationFacilityId().toString() : null);
        body.put("patientCpid", epcr.getPatientHealthId());
        body.put("emsMissionRef", m.getId().toString());
        body.put("missionState", m.getState());
        body.put("snapshot", buildSnapshot(epcr, events));
        try {
            preArrival.pushPreArrival(m.getTenantId(), body);
        } catch (RuntimeException e) {
            log.debug("pre-arrival push suppressed for mission {}: {}", m.getId(), e.getMessage());
        }
    }

    /** Compact clinical snapshot: latest vitals + GCS, the intervention list, and the narrative. */
    private Map<String, Object> buildSnapshot(EmsEpcrEntity epcr, List<EmsEpcrEventEntity> events) {
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<String, Object> latestVitals = null;
        Map<String, Object> latestGcs = null;
        List<Object> interventions = new ArrayList<>();
        for (EmsEpcrEventEntity ev : events) {
            Object payload = readJson(ev.getPayloadJson());
            switch (ev.getEventType()) {
                case "VITALS" -> { if (payload instanceof Map<?, ?> mp) latestVitals = cast(mp); }
                case "GCS" -> { if (payload instanceof Map<?, ?> mp) latestGcs = cast(mp); }
                case "INTERVENTION", "MEDICATION" -> { if (payload != null) interventions.add(payload); }
                default -> { /* NOTE etc. — narrative carries it */ }
            }
        }
        snap.put("latestVitals", latestVitals);
        snap.put("latestGcs", latestGcs);
        snap.put("interventions", interventions);
        snap.put("narrative", epcr.getNarrative());
        snap.put("obsCount", events.size());
        return snap;
    }

    private Map<String, Object> eventView(EmsEpcrEventEntity ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventType", ev.getEventType());
        m.put("channel", ev.getChannel());
        m.put("payload", readJson(ev.getPayloadJson()));
        m.put("recordedAt", ev.getRecordedAt() != null ? ev.getRecordedAt().toString() : null);
        return m;
    }

    private EmsMissionEntity requireMission(UUID tenantId, UUID missionId) {
        return missionRepo.findByIdAndTenantId(missionId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("EMS mission not found: " + missionId));
    }

    private static String subjectId(EmsMissionEntity m) {
        return m.getTraumaEpisodeId() != null ? m.getTraumaEpisodeId().toString() : m.getIncidentId().toString();
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
