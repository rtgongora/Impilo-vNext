package zw.gov.mohcc.impilo.daidzai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.integration.NdilaCatchmentClient;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsMissionEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsUnitEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyIncidentRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmsMissionRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmsUnitRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * EMS clinical-dispatch service (architecture decision #2). Owns the crew-to-patient mission
 * lifecycle: {@link #dispatch} creates the mission + unit and advances CREATED→DISPATCHED
 * (idempotent per incident); {@link #advance} walks the validated {@link EmsMissionState} machine to
 * HANDOVER→CLEARED. Every transition is written to the DAIDZAI outbox as a {@code daidzai.ems.*}
 * event and stamped onto the canonical trauma-episode timeline. Crew resolves from VASHANDI and the
 * ambulance is an asset-registry Object ID — both carried by value; routing from NDILA is best-effort.
 *
 * <p>This replaces the {@code OwnerRoutedGateway.requestDispatch} log-stub with a real mission.</p>
 */
@Service
public class EmsDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmsDispatchService.class);
    private static final String AGG = "EMS_MISSION";

    private final EmsMissionRepository missionRepo;
    private final EmsUnitRepository unitRepo;
    private final EmergencyIncidentRepository incidentRepo;
    private final ReferenceGenerator refs;
    private final DaidzaiEventEmitter emitter;
    private final TraumaEpisodeService traumaEpisodes;
    private final NdilaCatchmentClient catchment;

    public EmsDispatchService(EmsMissionRepository missionRepo, EmsUnitRepository unitRepo,
                              EmergencyIncidentRepository incidentRepo, ReferenceGenerator refs,
                              DaidzaiEventEmitter emitter, TraumaEpisodeService traumaEpisodes,
                              NdilaCatchmentClient catchment) {
        this.missionRepo = missionRepo;
        this.unitRepo = unitRepo;
        this.incidentRepo = incidentRepo;
        this.refs = refs;
        this.emitter = emitter;
        this.traumaEpisodes = traumaEpisodes;
        this.catchment = catchment;
    }

    /**
     * Dispatch a clinical EMS mission for an incident. Idempotent on {@code (tenant, incident)} — a
     * re-dispatch (e.g. retried request with the same Idempotency-Key) returns the existing mission
     * rather than forking a second crew. Creates the responding unit, resolves a destination facility
     * from NDILA (best-effort), advances CREATED→DISPATCHED, emits {@code daidzai.ems.mission_created}
     * + {@code daidzai.ems.dispatched}, and registers a DISPATCH phase on the trauma episode.
     */
    @Transactional
    public EmsMissionEntity dispatch(UUID tenantId, UUID incidentId, String callSign,
                                     String ambulanceAssetId, String crewJson, String priority) {
        EmergencyIncidentEntity inc = incidentRepo.findByIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));

        var existing = missionRepo.findByTenantIdAndIncidentId(tenantId, incidentId);
        if (existing.isPresent()) {
            return existing.get(); // idempotent — one clinical mission per incident
        }

        EmsUnitEntity unit = new EmsUnitEntity();
        unit.setTenantId(tenantId);
        unit.setUnitReference(refs.emsUnitReference());
        unit.setCallSign(callSign);
        unit.setAmbulanceAssetId(ambulanceAssetId);
        if (crewJson != null && !crewJson.isBlank()) unit.setCrewJson(crewJson);
        unit.setStatus("ASSIGNED");
        unit.setHomeFacilityId(inc.getFacilityId());
        unit = unitRepo.save(unit);

        EmsMissionEntity m = new EmsMissionEntity();
        m.setTenantId(tenantId);
        m.setMissionReference(refs.emsMissionReference());
        m.setIncidentId(incidentId);
        m.setTraumaEpisodeId(inc.getTraumaEpisodeId());
        m.setUnitId(unit.getId());
        m.setDispatchPriority(priority != null ? priority.toUpperCase() : inc.getSeverity());
        m.setState(EmsMissionState.CREATED.name());
        resolveDestination(tenantId, inc, m);
        try {
            m = missionRepo.saveAndFlush(m);
        } catch (DataIntegrityViolationException race) {
            // Concurrent dispatch won the (tenant, incident) unique race — return the winner.
            return missionRepo.findByTenantIdAndIncidentId(tenantId, incidentId).orElseThrow(() -> race);
        }
        emitEms(m, "daidzai.ems.mission_created", Map.of("unitId", unit.getId().toString(),
                "callSign", callSign == null ? "" : callSign));

        // CREATED → DISPATCHED in the same act of dispatching.
        m.setState(EmsMissionState.DISPATCHED.name());
        m.setDispatchedAt(OffsetDateTime.now());
        m = missionRepo.save(m);
        emitEms(m, "daidzai.ems.dispatched", Map.of("priority", m.getDispatchPriority() == null ? "" : m.getDispatchPriority()));
        registerPhase(m, "DISPATCH", "DISPATCHED", "daidzai.ems.dispatched");
        return m;
    }

    /**
     * Advance a mission to {@code toState}, validating the transition against {@link EmsMissionState}.
     * Records timestamps, emits {@code daidzai.ems.<state>}, and on HANDOVER stamps the PCT encounter
     * ref + registers a PREHOSPITAL phase on the trauma episode. Terminal states reject further moves.
     */
    @Transactional
    public EmsMissionEntity advance(UUID tenantId, UUID missionId, String toStateRaw,
                                    String note, String pctEncounterRef) {
        EmsMissionEntity m = getMission(tenantId, missionId);
        EmsMissionState from = EmsMissionState.of(m.getState());
        EmsMissionState to = EmsMissionState.of(toStateRaw);
        if (from == to) {
            return m; // idempotent no-op re-assertion of the current state
        }
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("Illegal EMS mission transition " + from + " -> " + to);
        }
        m.setState(to.name());
        OffsetDateTime now = OffsetDateTime.now();
        switch (to) {
            case ON_SCENE -> m.setOnSceneAt(now);
            case DEPARTED_SCENE -> m.setDepartedSceneAt(now);
            case ARRIVED_FACILITY -> m.setArrivedFacilityAt(now);
            case HANDOVER -> {
                m.setHandoverAt(now);
                if (pctEncounterRef != null && !pctEncounterRef.isBlank()) {
                    m.setPctEncounterRef(pctEncounterRef);
                }
            }
            case CLEARED -> m.setClearedAt(now);
            case STANDDOWN -> m.setStanddownReason(note);
            default -> { /* no timestamp column for intermediate states */ }
        }
        m = missionRepo.save(m);
        emitEms(m, "daidzai.ems." + to.name().toLowerCase(),
                note != null ? Map.of("note", note) : Map.of());

        if (to == EmsMissionState.HANDOVER) {
            registerPhase(m, "PREHOSPITAL", "HANDOVER", "daidzai.ems.handover");
        }
        return m;
    }

    @Transactional(readOnly = true)
    public EmsMissionEntity getMission(UUID tenantId, UUID missionId) {
        return missionRepo.findByIdAndTenantId(missionId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("EMS mission not found: " + missionId));
    }

    @Transactional(readOnly = true)
    public EmsMissionEntity missionForIncident(UUID tenantId, UUID incidentId) {
        return missionRepo.findByTenantIdAndIncidentId(tenantId, incidentId)
                .orElseThrow(() -> new NoSuchElementException("No EMS mission for incident: " + incidentId));
    }

    /** Best-effort NDILA routing: resolve the nearest catchment facility as the destination. */
    private void resolveDestination(UUID tenantId, EmergencyIncidentEntity inc, EmsMissionEntity m) {
        if (inc.getFacilityId() != null) {
            m.setDestinationFacilityId(inc.getFacilityId());
        }
        if (inc.getLocationLat() == null || inc.getLocationLng() == null) {
            return;
        }
        try {
            var facilities = catchment.nearestFacilities(tenantId, inc.getLocationLat(), inc.getLocationLng(), 1);
            if (!facilities.isEmpty()) {
                var nearest = facilities.get(0);
                try {
                    m.setDestinationFacilityId(UUID.fromString(nearest.locationId()));
                } catch (IllegalArgumentException ignore) {
                    // locationId not a UUID — keep the incident facility (if any); routing stays honest.
                }
                // Rough ETA at an urban ambulance ~40 km/h (~11 m/s); NDILA refines this in W4.
                m.setDestinationEtaSeconds((int) Math.round(nearest.distanceMeters() / 11.0));
            }
        } catch (RuntimeException e) {
            log.debug("NDILA routing unavailable for mission {} — destination stays best-effort: {}",
                    m.getMissionReference(), e.getMessage());
        }
    }

    private void registerPhase(EmsMissionEntity m, String phase, String status, String eventType) {
        if (m.getTraumaEpisodeId() == null) return;
        try {
            traumaEpisodes.registerPhase(m.getTenantId(), m.getTraumaEpisodeId(), phase,
                    "daidzai-ems", m.getId().toString(), status, eventType, null);
        } catch (RuntimeException e) {
            log.warn("EMS phase registration ({}) for episode {} failed: {}",
                    phase, m.getTraumaEpisodeId(), e.getMessage());
        }
    }

    private void emitEms(EmsMissionEntity m, String eventType, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("missionId", m.getId().toString());
        payload.put("missionReference", m.getMissionReference());
        payload.put("incidentId", m.getIncidentId().toString());
        payload.put("state", m.getState());
        if (m.getTraumaEpisodeId() != null) payload.put("traumaEpisodeId", m.getTraumaEpisodeId().toString());
        payload.putAll(extra);
        String subjectId = m.getTraumaEpisodeId() != null
                ? m.getTraumaEpisodeId().toString() : m.getIncidentId().toString();
        emitter.emit(AGG, m.getId().toString(), eventType, "TRAUMA_EPISODE", subjectId, payload, m.getTenantId());
    }
}
