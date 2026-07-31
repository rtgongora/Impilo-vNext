package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyHandoverRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.realtime.EmergencyRealtimePublisher;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Opens, reads and transitions emergency episodes.
 *
 * <h3>Care-first is a structural property here, not a policy statement</h3>
 *
 * <p>Minting an episode depends on nothing outside this service. It does not call daidzai to
 * correlate, VITO to identify, COSTA to authorise or TUSO to check capability, and it cannot be made
 * to — those are enrichments applied after the episode exists. A patient who arrives while a peer
 * service is down still gets an episode, and the peers that could not be reached are reported in the
 * response as {@code degraded} rather than raised as an error. The alternative, an emergency
 * registration that fails because a downstream service is unavailable, is the failure mode the
 * offline and resilience requirements exist to forbid.
 *
 * <h3>Idempotent mint</h3>
 *
 * <p>Replay is normal on this estate, not exceptional: an ambulance pre-alert is re-pushed, a clerk
 * registers the same walk-in twice, a Kafka consumer redelivers. Where an entry route carries a
 * source reference the mint is idempotent on it, matching
 * {@code uq_emergency_episode_entry_source}. Where it does not — a walk-in has nothing to key on —
 * two episodes are the correct answer, because two people can walk in.
 */
@Service
public class EmergencyEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyEpisodeService.class);

    private static final List<String> OPEN_STATES = List.of(
            EmergencyEpisodeState.PRE_ARRIVAL.name(),
            EmergencyEpisodeState.OPEN_UNTRIAGED.name(),
            EmergencyEpisodeState.OPEN_IN_CARE.name(),
            EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE.name());

    private final EmergencyEpisodeRepository repository;
    private final EmergencyHandoverRepository handoverRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<EmergencyRealtimePublisher> realtimePublisher;

    /**
     * All three collaborators are PCT's own tables. The outbox is a local write in the same
     * transaction — it is what lets this service tell peers what happened WITHOUT calling them,
     * which is how care-first and cross-service correlation coexist.
     *
     * <p>Realtime invalidation is optional ({@code pct.emergency.realtime-enabled}); when off the
     * Kafka outbox remains the only fan-out path.
     */
    public EmergencyEpisodeService(EmergencyEpisodeRepository repository,
                                   EmergencyHandoverRepository handoverRepository,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<EmergencyRealtimePublisher> realtimePublisher) {
        this.repository = repository;
        this.handoverRepository = handoverRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.realtimePublisher = realtimePublisher;
    }

    /** What was asked for. Peers are absent from this record on purpose — see the class comment. */
    public record OpenEpisodeCommand(
            UUID tenantId,
            UUID facilityId,
            String entryRoute,
            String entrySourceService,
            String entrySourceRef,
            String entryContextJson,
            String episodeClass,
            String subjectCpid,
            String journeyId,
            Long encounterId,
            UUID traumaEpisodeId,
            UUID emergencyCaseId,
            boolean sensitive,
            OffsetDateTime arrivedAt,
            String actorId) {
    }

    /**
     * @param episode  the episode, new or pre-existing
     * @param created  false when an idempotent replay returned the existing episode
     * @param degraded peers that could not be consulted; the episode exists regardless
     */
    public record OpenEpisodeResult(EmergencyEpisodeEntity episode, boolean created, List<String> degraded) {
    }

    @Transactional
    public OpenEpisodeResult open(OpenEpisodeCommand cmd) {
        require(cmd.tenantId() != null, "tenant_id is required");
        require(cmd.facilityId() != null, "facility_id is required");
        require(cmd.entryRoute() != null && !cmd.entryRoute().isBlank(), "entry_route is required");

        // Idempotent replay: same route, same source ref, same tenant.
        if (cmd.entrySourceRef() != null && !cmd.entrySourceRef().isBlank()) {
            Optional<EmergencyEpisodeEntity> existing = repository
                    .findByTenantIdAndEntryRouteAndEntrySourceRef(
                            cmd.tenantId(), cmd.entryRoute(), cmd.entrySourceRef());
            if (existing.isPresent()) {
                log.info("Emergency episode replay for {} {} — returning existing episode {}",
                        cmd.entryRoute(), cmd.entrySourceRef(), existing.get().getEpisodeId());
                return new OpenEpisodeResult(existing.get(), false, List.of());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        e.setEpisodeId(UUID.randomUUID());
        e.setTenantId(cmd.tenantId());
        e.setFacilityId(cmd.facilityId());
        e.setEpisodeReference(mintReference(now));
        e.setEntryRoute(cmd.entryRoute());
        e.setEntrySourceService(cmd.entrySourceService());
        e.setEntrySourceRef(cmd.entrySourceRef());
        e.setEntryContextJson(cmd.entryContextJson());
        e.setArrivedAt(cmd.arrivedAt() != null ? cmd.arrivedAt() : now);
        e.setEpisodeClass(cmd.episodeClass() != null ? cmd.episodeClass() : "UNDIFFERENTIATED");
        e.setSensitive(cmd.sensitive());
        e.setTraumaEpisodeId(cmd.traumaEpisodeId());
        e.setEmergencyCaseId(cmd.emergencyCaseId());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setCreatedBy(cmd.actorId());

        // Identity: care-first. An unknown patient is a first-class state, not a validation failure.
        if (cmd.subjectCpid() != null && !cmd.subjectCpid().isBlank()) {
            e.setSubjectCpid(cmd.subjectCpid());
            e.setIdentityMode("KNOWN");
            e.setIdentityResolvedAt(now);
        } else if (cmd.emergencyCaseId() != null) {
            e.setIdentityMode("PROVISIONAL");
        } else {
            e.setIdentityMode("UNKNOWN");
        }

        // The CC-5 anchor. A pre-arrival has none yet and that is the only state permitted to.
        if (cmd.journeyId() != null && !cmd.journeyId().isBlank()) {
            e.setJourneyId(cmd.journeyId());
            e.setEncounterId(cmd.encounterId());
            e.setAnchorResolvedAt(now);
            e.setState(EmergencyEpisodeState.OPEN_UNTRIAGED.name());
        } else {
            e.setState(EmergencyEpisodeState.PRE_ARRIVAL.name());
        }

        // Location starts confirmed at the point of registration, so the staleness clock runs from
        // a real observation rather than from null.
        e.setCurrentLocation(cmd.journeyId() != null ? "EMERGENCY_UNIT" : "INBOUND");
        e.setLocationConfirmedAt(now);

        EmergencyEpisodeEntity saved = repository.save(e);
        emit(saved, "EMERGENCY_EPISODE_OPENED", null);
        log.info("Emergency episode {} opened via {} in state {}",
                saved.getEpisodeReference(), saved.getEntryRoute(), saved.getState());
        return new OpenEpisodeResult(saved, true, List.of());
    }

    /**
     * Attach the PCT anchor to a pre-arrival episode when the patient actually arrives.
     *
     * <p>This is the CC-5 violation-to-close in operational form: an episode minted from a
     * prehospital pre-alert has no journey, and this is where it acquires one.
     */
    @Transactional
    public EmergencyEpisodeEntity resolveAnchorOnArrival(UUID episodeId, UUID tenantId,
                                                         String journeyId, Long encounterId) {
        EmergencyEpisodeEntity e = load(episodeId, tenantId);
        require(journeyId != null && !journeyId.isBlank(), "journey_id is required to resolve the anchor");

        if (e.getJourneyId() != null) {
            // Already anchored. Re-arrival is a replay, not a second journey.
            return e;
        }
        OffsetDateTime now = OffsetDateTime.now();
        e.setJourneyId(journeyId);
        e.setEncounterId(encounterId);
        e.setAnchorResolvedAt(now);
        transitionTo(e, EmergencyEpisodeState.OPEN_UNTRIAGED, now);
        e.setCurrentLocation("EMERGENCY_UNIT");
        e.setLocationConfirmedAt(now);
        EmergencyEpisodeEntity saved = repository.save(e);
        // daidzai back-fills its continuum link from this: it is the moment the prehospital episode
        // becomes resolvable to a PCT anchor, which is CC-5 violation V-3 closing in practice.
        emit(saved, "EMERGENCY_EPISODE_ANCHOR_RESOLVED", null);
        return saved;
    }

    /**
     * Apply a state transition, refusing any the graph does not allow.
     *
     * <p>The refusal is the point: it is what stops a referral request being recorded as a completed
     * handover. The database has the same constraint, so a caller that bypasses this service still
     * cannot write an episode closed as handed-over without a handover id.
     */
    @Transactional
    public EmergencyEpisodeEntity transition(UUID episodeId, UUID tenantId,
                                             EmergencyEpisodeState target, String outcome) {
        EmergencyEpisodeEntity e = load(episodeId, tenantId);
        EmergencyEpisodeState previous = EmergencyEpisodeState.valueOf(e.getState());
        OffsetDateTime now = OffsetDateTime.now();
        transitionTo(e, target, now);
        if (target.isTerminal() && target != EmergencyEpisodeState.MERGED) {
            require(outcome != null && !outcome.isBlank(),
                    "an outcome is required to close an episode: a closure with no outcome is counted "
                            + "as a completed one by every indicator built on this table");
            e.setOutcome(outcome);
            e.setClosedAt(now);
        }
        EmergencyEpisodeEntity saved = repository.save(e);
        emit(saved, target == EmergencyEpisodeState.MERGED
                ? "EMERGENCY_EPISODE_MERGED" : "EMERGENCY_EPISODE_STATE_CHANGED", previous.name());
        return saved;
    }

    private void transitionTo(EmergencyEpisodeEntity e, EmergencyEpisodeState target, OffsetDateTime now) {
        EmergencyEpisodeState current = EmergencyEpisodeState.valueOf(e.getState());
        if (current == target) {
            return;
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Emergency episode cannot move from " + current + " to " + target
                            + ". Allowed: " + current.allowedNext());
        }
        e.setState(target.name());
        e.setUpdatedAt(now);
    }

    @Transactional(readOnly = true)
    public EmergencyEpisodeEntity get(UUID episodeId, UUID tenantId) {
        return load(episodeId, tenantId);
    }

    /** The operational board: every open episode at a facility. */
    @Transactional(readOnly = true)
    public List<EmergencyEpisodeEntity> board(UUID tenantId, UUID facilityId) {
        return repository.findByTenantIdAndFacilityIdAndStateInOrderByArrivedAtDesc(
                tenantId, facilityId, OPEN_STATES);
    }

    /** Confirm where the patient is. Resets the staleness clock that detects a lost patient. */
    @Transactional
    public EmergencyEpisodeEntity confirmLocation(UUID episodeId, UUID tenantId, String location) {
        EmergencyEpisodeEntity e = load(episodeId, tenantId);
        require(location != null && !location.isBlank(), "a location is required");
        e.setCurrentLocation(location);
        e.setLocationConfirmedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return repository.save(e);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // The acceptance handshake (W9). V200 declared the invariant in a CHECK three waves before
    // there was any way to satisfy it: state <> 'CLOSED_HANDED_OVER' OR handover_id IS NOT NULL.
    // These four methods are what makes it real. See emergency_handover's V204 header for the
    // full rationale — the short version: responsibility transfers ONLY on a write by the
    // accepting party carrying its OWN record id, never on a request, a page, or a timeout.
    // ─────────────────────────────────────────────────────────────────────────────────────────

    private static final List<String> HANDOVER_TARGET_TYPES =
            List.of("ADMISSION", "THEATRE", "MENTAL_HEALTH", "INTERFACILITY_TRANSFER", "OTHER_SERVICE");

    /**
     * Per-target-type response window feeding {@code response_due_at} (V209) — the deadline the
     * auto-expiry sweep ({@link HandoverExpirySweepJob}) reads. THEATRE gets the tightest window: a
     * patient awaiting an emergency operating slot cannot wait as long as an interfacility transfer
     * negotiation reasonably can. These are seeds, like every other Zimbabwe time target in this
     * pack (R9's "every Zimbabwe time target in this pack is a seed") — not yet ratified.
     */
    private static final Map<String, Integer> HANDOVER_RESPONSE_WINDOW_MINUTES = Map.of(
            "THEATRE", 15,
            "ADMISSION", 30,
            "MENTAL_HEALTH", 30,
            "OTHER_SERVICE", 30,
            "INTERFACILITY_TRANSFER", 60);

    /**
     * Request a handover. Moves the episode OPEN_IN_CARE -> OPEN_AWAITING_ACCEPTANCE — a request is
     * not a handover, so the episode does not close and {@code handover_id} is not touched.
     *
     * <p>The FSM (via {@link #transition}) is the enforcement that this can only happen from
     * OPEN_IN_CARE: {@code EmergencyEpisodeState.OPEN_IN_CARE.allowedNext()} is the only state whose
     * transitions include OPEN_AWAITING_ACCEPTANCE.
     */
    @Transactional
    public EmergencyHandoverEntity requestHandover(UUID episodeId, UUID tenantId, String targetType,
                                                    String targetService, UUID targetFacilityId,
                                                    String requestedBy, String reason) {
        require(targetType != null && HANDOVER_TARGET_TYPES.contains(targetType),
                "target_type must be one of " + HANDOVER_TARGET_TYPES + ", got: " + targetType);
        require(requestedBy != null && !requestedBy.isBlank(), "requested_by is required");
        handoverRepository.findPending(tenantId, episodeId).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Episode " + episodeId + " already has a pending handover (" + existing.getHandoverId()
                            + ") to " + existing.getTargetType()
                            + " — decline or the request must resolve before a new one can be raised");
        });

        // Moves the episode to OPEN_AWAITING_ACCEPTANCE. Not terminal, so transition() requires no
        // outcome; this is the same tested state-machine path every other transition uses.
        transition(episodeId, tenantId, EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE, null);

        EmergencyHandoverEntity h = new EmergencyHandoverEntity();
        h.setTenantId(tenantId);
        h.setEpisodeId(episodeId);
        h.setTargetType(targetType);
        h.setTargetService(targetService);
        h.setTargetFacilityId(targetFacilityId);
        h.setRequestedBy(requestedBy);
        h.setRequestReason(reason);
        h.setStatus("PENDING");
        Integer windowMinutes = HANDOVER_RESPONSE_WINDOW_MINUTES.get(targetType);
        if (windowMinutes != null) {
            h.setResponseDueAt(OffsetDateTime.now().plusMinutes(windowMinutes));
        }
        EmergencyHandoverEntity saved = handoverRepository.saveAndFlush(h);
        emitHandover(saved, "EMERGENCY_HANDOVER_REQUESTED");
        return saved;
    }

    /**
     * Accept a handover. The caller must supply {@code acceptingRef} — the accepting party's OWN
     * record id (an admission id, a procedure_episode id, an mh_referral id). This is the one write
     * that closes the episode as CLOSED_HANDED_OVER; nothing else may.
     *
     * <p>Deliberately does not reuse {@link #transition}: it must set {@code handover_id} on the
     * SAME save as the state change, and {@code transition} loads and saves its own copy of the
     * entity, which would let the two writes race. A handful of duplicated lines here versus a
     * shared entity reference is the safer trade.
     */
    @Transactional
    public EmergencyEpisodeEntity acceptHandover(UUID handoverId, UUID tenantId, String acceptedBy,
                                                 String acceptingRef, String acceptingService) {
        EmergencyHandoverEntity h = loadHandover(handoverId, tenantId);
        require("PENDING".equals(h.getStatus()),
                "Handover " + handoverId + " is " + h.getStatus() + ", not PENDING — it cannot be accepted");
        require(acceptedBy != null && !acceptedBy.isBlank(), "accepted_by is required");
        require(acceptingRef != null && !acceptingRef.isBlank(),
                "accepting_ref is required — the accepting party's own record id is the handshake itself");

        OffsetDateTime now = OffsetDateTime.now();
        h.setStatus("ACCEPTED");
        h.setAcceptedBy(acceptedBy);
        h.setAcceptedAt(now);
        h.setAcceptingRef(acceptingRef);
        h.setAcceptingService(acceptingService);
        handoverRepository.save(h);
        emitHandover(h, "EMERGENCY_HANDOVER_ACCEPTED");

        EmergencyEpisodeEntity e = load(h.getEpisodeId(), tenantId);
        EmergencyEpisodeState previous = EmergencyEpisodeState.valueOf(e.getState());
        transitionTo(e, EmergencyEpisodeState.CLOSED_HANDED_OVER, now);
        e.setHandoverId(h.getHandoverId());
        e.setOutcome(outcomeForTarget(h.getTargetType()));
        e.setClosedAt(now);
        EmergencyEpisodeEntity saved = repository.save(e);
        emit(saved, "EMERGENCY_EPISODE_STATE_CHANGED", previous.name());
        return saved;
    }

    private static String outcomeForTarget(String targetType) {
        return switch (targetType) {
            case "ADMISSION" -> "ADMITTED";
            case "THEATRE" -> "TO_THEATRE";
            case "MENTAL_HEALTH" -> "TO_MENTAL_HEALTH";
            case "INTERFACILITY_TRANSFER" -> "TRANSFERRED";
            default -> "HANDED_OVER";
        };
    }

    /**
     * Decline a handover. Returns the episode to OPEN_IN_CARE — never to a closed state. No timeout
     * discharges responsibility; a declined request just means this attempt did not succeed.
     */
    @Transactional
    public EmergencyEpisodeEntity declineHandover(UUID handoverId, UUID tenantId, String declinedBy, String reason) {
        EmergencyHandoverEntity h = loadHandover(handoverId, tenantId);
        require("PENDING".equals(h.getStatus()),
                "Handover " + handoverId + " is " + h.getStatus() + ", not PENDING — it cannot be declined");
        require(declinedBy != null && !declinedBy.isBlank(), "declined_by is required");
        require(reason != null && !reason.isBlank(), "a decline reason is required");

        h.setStatus("DECLINED");
        h.setDeclinedBy(declinedBy);
        h.setDeclinedAt(OffsetDateTime.now());
        h.setDeclineReason(reason);
        handoverRepository.save(h);
        emitHandover(h, "EMERGENCY_HANDOVER_DECLINED");

        return transition(h.getEpisodeId(), tenantId, EmergencyEpisodeState.OPEN_IN_CARE, null);
    }

    /**
     * Expire a handover: a decision (by a clinician, or {@link HandoverExpirySweepJob} once
     * {@code response_due_at} has passed) that this attempt has gone unanswered too long. Returns the
     * episode to OPEN_IN_CARE — same as a decline. {@code ritoCaseRef} is a link only, supplied by the
     * caller — this method never calls rito itself. {@link HandoverExpirySweepJob} opens the case
     * synchronously (via {@code RitoSafetyClient}, matching inpatient-theatre's own pattern) BEFORE
     * calling this method and passes the resulting ref straight through; a manual clinician-driven
     * expiry over HTTP may likewise supply an already-opened case's ref, or leave it absent.
     */
    @Transactional
    public EmergencyEpisodeEntity expireHandover(UUID handoverId, UUID tenantId, String expiredBy, String ritoCaseRef) {
        EmergencyHandoverEntity h = loadHandover(handoverId, tenantId);
        require("PENDING".equals(h.getStatus()),
                "Handover " + handoverId + " is " + h.getStatus() + ", not PENDING — it cannot expire");
        require(expiredBy != null && !expiredBy.isBlank(), "expired_by is required");

        h.setStatus("EXPIRED");
        h.setExpiredAt(OffsetDateTime.now());
        h.setExpiredBy(expiredBy);
        h.setRitoCaseRef(ritoCaseRef);
        handoverRepository.save(h);
        emitHandover(h, "EMERGENCY_HANDOVER_EXPIRED");

        return transition(h.getEpisodeId(), tenantId, EmergencyEpisodeState.OPEN_IN_CARE, null);
    }

    @Transactional(readOnly = true)
    public List<EmergencyHandoverEntity> handoverHistory(UUID episodeId, UUID tenantId) {
        return handoverRepository.findByTenantIdAndEpisodeIdOrderByRequestedAtDesc(tenantId, episodeId);
    }

    private EmergencyHandoverEntity loadHandover(UUID handoverId, UUID tenantId) {
        return handoverRepository.findByHandoverIdAndTenantId(handoverId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Emergency handover not found in this tenant: " + handoverId));
    }

    private void emitHandover(EmergencyHandoverEntity h, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", eventType);
        payload.put("handoverId", h.getHandoverId() != null ? h.getHandoverId().toString() : null);
        payload.put("episodeId", h.getEpisodeId().toString());
        payload.put("episode_id", h.getEpisodeId().toString());
        payload.put("tenantId", h.getTenantId().toString());
        payload.put("tenant_id", h.getTenantId().toString());
        payload.put("targetType", h.getTargetType());
        payload.put("targetService", h.getTargetService());
        payload.put("status", h.getStatus());
        payload.put("acceptingRef", h.getAcceptingRef());
        payload.put("acceptingService", h.getAcceptingService());
        payload.put("ritoCaseRef", h.getRitoCaseRef());
        payload.put("rito_case_ref", h.getRitoCaseRef());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("EMERGENCY_HANDOVER");
        outbox.setAggregateId(h.getEpisodeId().toString());
        outbox.setEventType(eventType);
        outbox.setTenantId(h.getTenantId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.error("Emergency handover payload could not be serialised: {}", ex.getMessage());
            outbox.setPayload("{\"episodeId\":\"" + h.getEpisodeId() + "\"}");
        }
        outboxRepository.save(outbox);
    }

    private EmergencyEpisodeEntity load(UUID episodeId, UUID tenantId) {
        return repository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Emergency episode not found in this tenant: " + episodeId));
    }

    /**
     * A human-quotable reference. Emergency staff read these aloud across a resuscitation room, so
     * it is short and uses no characters that sound alike.
     */
    private static String mintReference(OffsetDateTime at) {
        final String alphabet = "0123456789ACDEFGHJKLMNPQRTUVWXY";
        StringBuilder sb = new StringBuilder("EM-");
        sb.append(at.getYear() % 100);
        sb.append(String.format("%02d", at.getMonthValue()));
        sb.append('-');
        for (int i = 0; i < 5; i++) {
            sb.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Write a lifecycle event to PCT's outbox.
     *
     * <p>The payload deliberately carries no clinical content — the episode id, the state and the
     * anchors, and nothing a consumer would be tempted to treat as the record. A consumer that needs
     * the clinical detail reads it from the owning service; an event that carried a copy would be a
     * second, staler answer to the same question.
     *
     * <p>{@code subjectCpid} is included because peers key their own repointing on it, but note it is
     * the same attribute the identity hook rewrites — a consumer must not persist it as a join key.
     */
    private void emit(EmergencyEpisodeEntity e, String eventType, String previousState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // event_type is required on the wire: OutboxPublisher sends the payload as-is, and
        // reporting-service's EmergencyReportingConsumer keys off it (Theatre pattern).
        payload.put("event_type", eventType);
        payload.put("episodeId", e.getEpisodeId().toString());
        payload.put("episode_id", e.getEpisodeId().toString());
        payload.put("episodeReference", e.getEpisodeReference());
        payload.put("tenantId", e.getTenantId().toString());
        payload.put("tenant_id", e.getTenantId().toString());
        payload.put("facilityId", e.getFacilityId().toString());
        payload.put("facility_id", e.getFacilityId().toString());
        payload.put("state", e.getState());
        payload.put("previousState", previousState);
        payload.put("entryRoute", e.getEntryRoute());
        payload.put("episodeClass", e.getEpisodeClass());
        payload.put("journeyId", e.getJourneyId());
        payload.put("encounterId", e.getEncounterId());
        payload.put("traumaEpisodeId", e.getTraumaEpisodeId() != null ? e.getTraumaEpisodeId().toString() : null);
        payload.put("subjectCpid", e.getSubjectCpid());
        payload.put("identityMode", e.getIdentityMode());
        payload.put("sensitive", e.isSensitive());
        payload.put("outcome", e.getOutcome());
        if (e.getArrivedAt() != null) {
            payload.put("arrivedAt", e.getArrivedAt().toString());
            payload.put("openedAt", e.getArrivedAt().toString());
        }

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("EMERGENCY_EPISODE");
        outbox.setAggregateId(e.getEpisodeId().toString());
        outbox.setEventType(eventType);
        outbox.setTenantId(e.getTenantId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            // An unserialisable payload must not lose the event. The type and aggregate id are the
            // parts a consumer needs to go and read the truth for itself.
            log.error("Emergency episode {} payload could not be serialised: {}",
                    e.getEpisodeId(), ex.getMessage());
            outbox.setPayload("{\"episodeId\":\"" + e.getEpisodeId() + "\"}");
        }
        outboxRepository.save(outbox);

        // Optional invalidation hint (env-gated). Never replaces the Kafka outbox.
        EmergencyRealtimePublisher publisher = realtimePublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publishEpisodeHint(e.getTenantId(), e.getEpisodeId(), eventType);
        }
    }

    /** Reported to the caller so a clinician sees what could not be consulted. Never thrown. */
    static List<String> degradedPeers(String... names) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (n != null) out.add(n);
        }
        return List.copyOf(out);
    }
}
