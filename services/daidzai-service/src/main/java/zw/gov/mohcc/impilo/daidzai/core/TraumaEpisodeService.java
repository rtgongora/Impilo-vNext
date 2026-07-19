package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodeEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodePhaseEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.TraumaEpisodePhaseRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.TraumaEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Canonical trauma-episode spine (architecture decision #1). DAIDZAI owns a thin correlation
 * aggregate — the episode identity, the subject anchor, and a read-model phase timeline — and
 * references peer clinical/blood/EMS truth by id only. It never becomes a cross-service
 * orchestrator of clinical records; each phase owner keeps its own SoR rows stamped with the
 * shared {@code trauma_episode_id}.
 *
 * <p>Mint is idempotent on {@code (tenant, originKey)}: DAIDZAI mints on incident triage
 * (originKey = incident id) and PCT mints on ED-first walk-in trauma (originKey = ed_visit id).
 * A retried mint with the same origin key returns the existing episode — the spine never forks.</p>
 */
@Service
public class TraumaEpisodeService {

    public static final String OWNER_DAIDZAI = "daidzai";

    private final TraumaEpisodeRepository episodeRepo;
    private final TraumaEpisodePhaseRepository phaseRepo;
    private final ReferenceGenerator refs;
    private final DaidzaiEventEmitter emitter;

    public TraumaEpisodeService(TraumaEpisodeRepository episodeRepo, TraumaEpisodePhaseRepository phaseRepo,
                                ReferenceGenerator refs, DaidzaiEventEmitter emitter) {
        this.episodeRepo = episodeRepo;
        this.phaseRepo = phaseRepo;
        this.refs = refs;
        this.emitter = emitter;
    }

    /**
     * Idempotent dual-entry mint. Returns the existing episode when {@code (tenant, originKey)} has
     * already been minted; otherwise creates the episode, records its first phase, and emits
     * {@code daidzai.trauma_episode.minted}. Safe under a concurrent racing mint (the DB unique
     * constraint collapses the race and we re-read the winner).
     *
     * @param originService  minting service — {@value #OWNER_DAIDZAI} or {@code pct-service}
     * @param originKind     {@code INCIDENT} (daidzai) or {@code ED_WALK_IN} (pct)
     * @param originKey      the minting row's id (incident id / ed_visit id) — the idempotency key
     * @param firstPhase     the phase to seed the timeline with (e.g. {@code INCIDENT} / {@code ED})
     */
    @Transactional
    public TraumaEpisodeEntity mint(UUID tenantId, String originService, String originKind, String originKey,
                                    UUID incidentId, String subjectIdentityMode, String subjectCpid,
                                    String subjectTempRef, String firstPhase, String ownerRef) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (originKey == null || originKey.isBlank()) throw new IllegalArgumentException("originKey is required");

        var existing = episodeRepo.findByTenantIdAndOriginKey(tenantId, originKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        TraumaEpisodeEntity ep = new TraumaEpisodeEntity();
        ep.setTenantId(tenantId);
        ep.setEpisodeReference(refs.traumaEpisodeReference());
        ep.setOriginService(originService != null ? originService : OWNER_DAIDZAI);
        ep.setOriginKind(originKind != null ? originKind.toUpperCase() : "INCIDENT");
        ep.setOriginKey(originKey);
        ep.setIncidentId(incidentId);
        ep.setSubjectIdentityMode(subjectIdentityMode != null ? subjectIdentityMode.toUpperCase() : "UNKNOWN");
        ep.setSubjectCpid(subjectCpid);
        ep.setSubjectTempRef(subjectTempRef);
        ep.setStatus("OPEN");
        String phase = firstPhase != null ? firstPhase.toUpperCase() : "INCIDENT";
        ep.setCurrentPhase(phase);
        try {
            ep = episodeRepo.saveAndFlush(ep);
        } catch (DataIntegrityViolationException race) {
            // A concurrent mint won the (tenant, origin_key) unique race — return the winner.
            return episodeRepo.findByTenantIdAndOriginKey(tenantId, originKey)
                    .orElseThrow(() -> race);
        }

        recordPhaseInternal(ep, phase, ep.getOriginService(), ownerRef != null ? ownerRef : originKey,
                "MINTED", "daidzai.trauma_episode.minted", null);

        emitter.emit("TRAUMA_EPISODE", ep.getId().toString(), "daidzai.trauma_episode.minted",
                "TRAUMA_EPISODE", ep.getId().toString(),
                Map.of("episodeReference", ep.getEpisodeReference(),
                        "originService", ep.getOriginService(), "originKind", ep.getOriginKind(),
                        "originKey", ep.getOriginKey(),
                        "subjectIdentityMode", ep.getSubjectIdentityMode(),
                        "firstPhase", phase), tenantId);
        return ep;
    }

    /**
     * Register a phase-owner event onto the read-model timeline. Idempotent on
     * {@code (episode, owner_service, owner_ref, phase)} — a redelivered/retried registration
     * returns the existing row without duplicating it or re-advancing the episode.
     */
    @Transactional
    public TraumaEpisodePhaseEntity registerPhase(UUID tenantId, UUID episodeId, String phase,
                                                  String ownerService, String ownerRef, String status,
                                                  String eventType, String payloadJson) {
        TraumaEpisodeEntity ep = getEpisode(tenantId, episodeId);
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("phase is required");
        if (ownerService == null || ownerService.isBlank()) throw new IllegalArgumentException("ownerService is required");
        if (ownerRef == null || ownerRef.isBlank()) throw new IllegalArgumentException("ownerRef is required");
        return recordPhaseInternal(ep, phase.toUpperCase(), ownerService, ownerRef, status, eventType, payloadJson);
    }

    private TraumaEpisodePhaseEntity recordPhaseInternal(TraumaEpisodeEntity ep, String phase,
                                                         String ownerService, String ownerRef, String status,
                                                         String eventType, String payloadJson) {
        var existing = phaseRepo.findByTenantIdAndTraumaEpisodeIdAndOwnerServiceAndOwnerRefAndPhase(
                ep.getTenantId(), ep.getId(), ownerService, ownerRef, phase);
        if (existing.isPresent()) {
            return existing.get();
        }
        TraumaEpisodePhaseEntity row = new TraumaEpisodePhaseEntity();
        row.setTenantId(ep.getTenantId());
        row.setTraumaEpisodeId(ep.getId());
        row.setPhase(phase);
        row.setOwnerService(ownerService);
        row.setOwnerRef(ownerRef);
        row.setStatus(status);
        row.setEventType(eventType);
        row.setPayloadJson(payloadJson);
        row.setOccurredAt(OffsetDateTime.now());
        TraumaEpisodePhaseEntity saved;
        try {
            saved = phaseRepo.saveAndFlush(row);
        } catch (DataIntegrityViolationException race) {
            return phaseRepo.findByTenantIdAndTraumaEpisodeIdAndOwnerServiceAndOwnerRefAndPhase(
                    ep.getTenantId(), ep.getId(), ownerService, ownerRef, phase).orElseThrow(() -> race);
        }
        // Advance the episode's current phase pointer (timeline order = arrival order in W1).
        if (!phase.equals(ep.getCurrentPhase())) {
            ep.setCurrentPhase(phase);
            episodeRepo.save(ep);
        }
        emitter.emit("TRAUMA_EPISODE", ep.getId().toString(), "daidzai.trauma_episode.phase_recorded",
                "TRAUMA_EPISODE", ep.getId().toString(),
                Map.of("phase", phase, "ownerService", ownerService, "ownerRef", ownerRef,
                        "status", status == null ? "" : status), ep.getTenantId());
        return saved;
    }

    /** Close the episode (disposition — fleshed out in W6). Idempotent. */
    @Transactional
    public TraumaEpisodeEntity close(UUID tenantId, UUID episodeId, String reason) {
        TraumaEpisodeEntity ep = getEpisode(tenantId, episodeId);
        if ("CLOSED".equals(ep.getStatus())) {
            return ep;
        }
        ep.setStatus("CLOSED");
        ep.setClosedAt(OffsetDateTime.now());
        ep.setCloseReason(reason);
        ep = episodeRepo.save(ep);
        emitter.emit("TRAUMA_EPISODE", ep.getId().toString(), "daidzai.trauma_episode.closed",
                "TRAUMA_EPISODE", ep.getId().toString(),
                Map.of("reason", reason == null ? "" : reason), tenantId);
        return ep;
    }

    /** True if a {@code (tenant, originKey)} episode already exists (for mint 200-vs-201 signalling). */
    @Transactional(readOnly = true)
    public boolean episodeByOriginExists(UUID tenantId, String originKey) {
        return originKey != null && episodeRepo.findByTenantIdAndOriginKey(tenantId, originKey).isPresent();
    }

    @Transactional(readOnly = true)
    public TraumaEpisodeEntity getEpisode(UUID tenantId, UUID episodeId) {
        return episodeRepo.findByIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Trauma episode not found: " + episodeId));
    }

    @Transactional(readOnly = true)
    public List<TraumaEpisodePhaseEntity> timeline(UUID tenantId, UUID episodeId) {
        getEpisode(tenantId, episodeId);
        return phaseRepo.findByTraumaEpisodeIdOrderByOccurredAtAsc(episodeId);
    }

    /** Episode + ordered phase timeline as a plain map (the resolvable read-model view). */
    @Transactional(readOnly = true)
    public Map<String, Object> episodeView(UUID tenantId, UUID episodeId) {
        TraumaEpisodeEntity ep = getEpisode(tenantId, episodeId);
        List<TraumaEpisodePhaseEntity> phases = phaseRepo.findByTraumaEpisodeIdOrderByOccurredAtAsc(episodeId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("traumaEpisodeId", ep.getId().toString());
        view.put("episodeReference", ep.getEpisodeReference());
        view.put("status", ep.getStatus());
        view.put("currentPhase", ep.getCurrentPhase());
        view.put("originService", ep.getOriginService());
        view.put("originKind", ep.getOriginKind());
        view.put("subjectIdentityMode", ep.getSubjectIdentityMode());
        view.put("subjectCpid", ep.getSubjectCpid());
        view.put("incidentId", ep.getIncidentId() != null ? ep.getIncidentId().toString() : null);
        List<Map<String, Object>> timeline = phases.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phase", p.getPhase());
            m.put("ownerService", p.getOwnerService());
            m.put("ownerRef", p.getOwnerRef());
            m.put("status", p.getStatus());
            m.put("eventType", p.getEventType());
            m.put("occurredAt", p.getOccurredAt() != null ? p.getOccurredAt().toString() : null);
            return m;
        }).toList();
        view.put("timeline", timeline);
        return view;
    }
}
