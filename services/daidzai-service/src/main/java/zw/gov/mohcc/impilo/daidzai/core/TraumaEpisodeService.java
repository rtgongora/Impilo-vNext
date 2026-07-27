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
    /**
     * Resolve the episode to its PCT anchor when the patient reaches a facility — the flow that
     * closes CC-5 violation V-3.
     *
     * <p>The spine exists to cover the prehospital window in which no PCT anchor yet exists; this is
     * where that window ends. PCT calls it on facility arrival (ED-visit record / EMS arrival),
     * passing the journey the patient is now on and, when known, the emergency episode. From here
     * the trauma spine resolves to the continuum rather than floating beside it on {@code
     * subject_cpid} alone.
     *
     * <p><b>First anchor wins.</b> Re-linking to the same journey is a no-op (PCT retries, redelivered
     * events). A link to a <em>different</em> journey is refused rather than silently overwritten: a
     * single facility visit is one journey (CC-0), and an episode that appears to change journeys is
     * a correlation error a human must see, not a value to clobber. The exception path is where an
     * {@link #adopt} belongs instead.
     *
     * <p>Idempotent and safe to call best-effort: it never enforces via a schema CHECK (V200 tried
     * that and broke the live phase-advance — see V201), so a phase can advance before the link
     * arrives; the unanchored-facility-phase sweep is what surfaces a link that never came.
     */
    @Transactional
    public TraumaEpisodeEntity continuumLink(UUID tenantId, UUID episodeId,
                                             String pctJourneyId, UUID pctEmergencyEpisodeId) {
        if (pctJourneyId == null || pctJourneyId.isBlank()) {
            throw new IllegalArgumentException("pctJourneyId is required to anchor the episode");
        }
        TraumaEpisodeEntity ep = getEpisode(tenantId, episodeId);

        if (ep.getPctJourneyId() != null) {
            if (ep.getPctJourneyId().equals(pctJourneyId)) {
                // Already anchored to this journey — a retry or a redelivery. No-op, no second event.
                if (pctEmergencyEpisodeId != null && ep.getPctEmergencyEpisodeId() == null) {
                    ep.setPctEmergencyEpisodeId(pctEmergencyEpisodeId);
                    ep = episodeRepo.save(ep);
                }
                return ep;
            }
            throw new IllegalStateException(
                    "trauma episode " + episodeId + " is already anchored to journey "
                            + ep.getPctJourneyId() + " and cannot be re-anchored to " + pctJourneyId
                            + " — one facility visit is one journey; this is a correlation error, not a "
                            + "re-link. If two episodes describe one patient, adopt one into the other.");
        }

        ep.setPctJourneyId(pctJourneyId);
        ep.setPctEmergencyEpisodeId(pctEmergencyEpisodeId);
        ep.setContinuumLinkedAt(OffsetDateTime.now());
        ep = episodeRepo.save(ep);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pctJourneyId", pctJourneyId);
        payload.put("pctEmergencyEpisodeId", pctEmergencyEpisodeId == null ? null : pctEmergencyEpisodeId.toString());
        payload.put("currentPhase", ep.getCurrentPhase());
        emitter.emit("TRAUMA_EPISODE", ep.getId().toString(), "daidzai.trauma_episode.continuum_linked",
                "TRAUMA_EPISODE", ep.getId().toString(), payload, tenantId);
        return ep;
    }

    /**
     * Absorb one episode into another when both describe the same patient — the two-entry-path
     * duplicate the {@code (tenant, origin_key)} mint guard cannot prevent. Someone who phones the
     * SOS line (daidzai mints on the incident) and also walks in (PCT mints on the ed_visit) has two
     * episodes for one patient; this collapses them.
     *
     * <p>The absorbed episode is <b>never deleted</b> — a correlation already stamped with its id
     * must still resolve, and its timeline is evidence. It is marked {@code MERGED} with
     * {@code merged_into_id} pointing at the survivor. {@code status} is a free column with no CHECK,
     * so MERGED needs no schema change; the timeline sweep and the mint guard both read it.
     *
     * @param survivingId the episode that continues
     * @param absorbedId  the episode folded into it
     */
    @Transactional
    public TraumaEpisodeEntity adopt(UUID tenantId, UUID survivingId, UUID absorbedId, String reason) {
        if (survivingId == null || absorbedId == null) {
            throw new IllegalArgumentException("both surviving and absorbed episode ids are required");
        }
        if (survivingId.equals(absorbedId)) {
            throw new IllegalArgumentException("an episode cannot be adopted into itself");
        }
        TraumaEpisodeEntity surviving = getEpisode(tenantId, survivingId);
        TraumaEpisodeEntity absorbed = getEpisode(tenantId, absorbedId);

        if ("MERGED".equals(absorbed.getStatus())) {
            if (survivingId.equals(absorbed.getMergedIntoId())) {
                return absorbed; // already adopted into this survivor — idempotent replay
            }
            throw new IllegalStateException(
                    "episode " + absorbedId + " is already merged into " + absorbed.getMergedIntoId()
                            + "; it cannot be re-adopted into a different survivor");
        }

        absorbed.setStatus("MERGED");
        absorbed.setMergedIntoId(survivingId);
        absorbed.setMergedAt(OffsetDateTime.now());
        absorbed.setMergeReason(reason);
        // If the absorbed episode carried an anchor the survivor lacks, carry it across so the
        // surviving spine stays resolvable to the continuum.
        if (surviving.getPctJourneyId() == null && absorbed.getPctJourneyId() != null) {
            surviving.setPctJourneyId(absorbed.getPctJourneyId());
            surviving.setPctEmergencyEpisodeId(absorbed.getPctEmergencyEpisodeId());
            surviving.setContinuumLinkedAt(OffsetDateTime.now());
            episodeRepo.save(surviving);
        }
        absorbed = episodeRepo.save(absorbed);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("survivingEpisodeId", survivingId.toString());
        payload.put("reason", reason == null ? "" : reason);
        emitter.emit("TRAUMA_EPISODE", absorbed.getId().toString(), "daidzai.trauma_episode.merged",
                "TRAUMA_EPISODE", absorbed.getId().toString(), payload, tenantId);
        return absorbed;
    }

    /**
     * Episodes that reached a facility phase and never acquired a PCT anchor — the sweep that
     * enforces V-3 without a schema CHECK. This is the honest replacement for the constraint that
     * broke the live phase-advance: a phase may advance before the link arrives, but an OPEN episode
     * sitting in a facility phase with no journey is a correlation gap a human must close.
     */
    @Transactional(readOnly = true)
    public List<TraumaEpisodeEntity> unanchoredInFacility(UUID tenantId) {
        return episodeRepo.findUnanchoredFacilityPhase(tenantId);
    }

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
        // Continuum-link status — so a reader can tell an episode resolved to the continuum from one
        // still floating on subject_cpid alone. This is the visible face of V-3 closing.
        view.put("episodeClass", ep.getEpisodeClass());
        view.put("pctJourneyId", ep.getPctJourneyId());
        view.put("pctEmergencyEpisodeId",
                ep.getPctEmergencyEpisodeId() != null ? ep.getPctEmergencyEpisodeId().toString() : null);
        view.put("continuumLinkedAt",
                ep.getContinuumLinkedAt() != null ? ep.getContinuumLinkedAt().toString() : null);
        view.put("continuumLinked", ep.getPctJourneyId() != null);
        view.put("mergedIntoId", ep.getMergedIntoId() != null ? ep.getMergedIntoId().toString() : null);
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
