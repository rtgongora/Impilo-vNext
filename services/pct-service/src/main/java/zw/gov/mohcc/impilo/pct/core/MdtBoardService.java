package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineEventVersion;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtCaseItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtSessionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MdtCaseItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MdtSessionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TM-B15 (B15-1): MDT / specialist-board spine — spec session profile #9 ("PCT case + MDT record").
 *
 * <p>A chaired board reviews a case agenda; the outcome per case is a RECOMMENDATION (never an
 * order) with per-participant consensus/dissent positions, recorded by chair/scribe. Closing a case
 * item writes the consensus package back onto the presented referral via the canonical respond path
 * so the referral lifecycle stays the single source of truth.</p>
 *
 * <p>Identity visibility is enforced SERVER-SIDE here (acceptance: "pseudonymised presentation
 * enforced server-side"): under PSEUDONYMISED/ANONYMISED, non-privileged viewers get "Case N"
 * pseudonyms and no patient identifier in the board read model. Privileged = chair, scribe, and the
 * presenting clinician of the item.</p>
 */
@Service
public class MdtBoardService {

    private static final Logger log = LoggerFactory.getLogger(MdtBoardService.class);
    private static final Set<String> VISIBILITY_LEVELS =
            Set.of("FULL", "CARE_TEAM_LIMITED", "PSEUDONYMISED", "ANONYMISED");
    private static final Set<String> POSITIONS = Set.of("AGREE", "DISSENT");

    private final MdtSessionRepository sessionRepository;
    private final MdtCaseItemRepository caseItemRepository;
    private final ReferralRepository referralRepository;
    private final EventOutboxRepository outboxRepository;
    private final TelemedicineOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    public MdtBoardService(MdtSessionRepository sessionRepository,
                           MdtCaseItemRepository caseItemRepository,
                           ReferralRepository referralRepository,
                           EventOutboxRepository outboxRepository,
                           TelemedicineOrchestrationService orchestrationService,
                           ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.caseItemRepository = caseItemRepository;
        this.referralRepository = referralRepository;
        this.outboxRepository = outboxRepository;
        this.orchestrationService = orchestrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createSession(Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        String chairId = str(request, "chair_id", "chairId");
        if (blank(chairId)) chairId = ctx.actorId();
        if (blank(chairId)) {
            throw new PctDomainException("CHAIR_REQUIRED", 400, "an MDT session requires a chair.");
        }
        String visibility = str(request, "identity_visibility", "identityVisibility");
        visibility = blank(visibility) ? "PSEUDONYMISED" : visibility.trim().toUpperCase(Locale.ROOT);
        if (!VISIBILITY_LEVELS.contains(visibility)) {
            throw new PctDomainException("INVALID_VISIBILITY", 400,
                    "identity_visibility must be one of " + VISIBILITY_LEVELS);
        }

        MdtSessionEntity entity = new MdtSessionEntity();
        entity.setMdtSessionId(UUID.randomUUID());
        entity.setTenantId(ctx.tenantId());
        entity.setTitle(str(request, "title"));
        entity.setChairId(chairId);
        entity.setScribeId(str(request, "scribe_id", "scribeId"));
        entity.setSpecialty(str(request, "specialty"));
        entity.setRoutingPoolId(str(request, "routing_pool_id", "routingPoolId"));
        entity.setIdentityVisibility(visibility);
        String scheduledAt = str(request, "scheduled_at", "scheduledAt");
        if (!blank(scheduledAt)) {
            try {
                entity.setScheduledAt(OffsetDateTime.parse(scheduledAt.trim()));
            } catch (java.time.format.DateTimeParseException ex) {
                throw new PctDomainException("INVALID_TIMESTAMP", 400, "scheduled_at must be ISO-8601.");
            }
        }
        MdtSessionEntity saved = sessionRepository.save(entity);
        emit("telemedicine.session.mdt_created", saved.getMdtSessionId().toString(), ctx.tenantId(),
                Map.of("chairId", chairId, "identityVisibility", visibility));
        return sessionSummary(saved, 0);
    }

    @Transactional
    public Map<String, Object> addCase(String mdtSessionId, Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        MdtSessionEntity session = getSession(ctx, mdtSessionId);
        if ("CLOSED".equals(session.getStatus())) {
            throw new PctDomainException("MDT_SESSION_CLOSED", 409, "cannot add a case to a closed board.");
        }
        String referralIdRaw = str(request, "referral_id", "referralId");
        if (blank(referralIdRaw)) {
            throw new PctDomainException("REFERRAL_REQUIRED", 400, "a case item requires referral_id.");
        }
        UUID referralId = parseUuid(referralIdRaw);
        ReferralEntity referral = referralRepository.findByTenantIdAndReferralId(ctx.tenantId(), referralId)
                .orElseThrow(() -> new PctDomainException("MISSING_REFERRAL", 404,
                        "Referral not found: " + referralIdRaw));

        MdtCaseItemEntity item = new MdtCaseItemEntity();
        item.setCaseItemId(UUID.randomUUID());
        item.setMdtSessionId(session.getMdtSessionId());
        item.setTenantId(ctx.tenantId());
        item.setOrdinal(caseItemRepository.countByMdtSessionId(session.getMdtSessionId()) + 1);
        item.setReferralId(referral.getReferralId());
        item.setPresenterId(defaulted(str(request, "presenter_id", "presenterId"), ctx.actorId()));
        MdtCaseItemEntity saved = caseItemRepository.save(item);
        emit("telemedicine.session.mdt_case_added", session.getMdtSessionId().toString(), ctx.tenantId(),
                Map.of("caseItemId", saved.getCaseItemId().toString(),
                        "referralId", referral.getReferralId().toString(),
                        "ordinal", saved.getOrdinal()));
        return Map.of("caseItemId", saved.getCaseItemId().toString(),
                "ordinal", saved.getOrdinal(), "status", saved.getStatus());
    }

    /**
     * Record the board outcome for a case item and close it: recommendation text + per-participant
     * AGREE/DISSENT positions (dissent requires a note — no silent disagreement), then write the
     * consensus package back to the referral via the canonical respond path.
     */
    @Transactional
    public Map<String, Object> recordOutcome(String caseItemId, Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        MdtCaseItemEntity item = caseItemRepository
                .findByTenantIdAndCaseItemId(ctx.tenantId(), parseUuid(caseItemId))
                .orElseThrow(() -> new PctDomainException("MISSING_CASE_ITEM", 404,
                        "MDT case item not found: " + caseItemId));
        if ("CLOSED".equals(item.getStatus())) {
            throw new PctDomainException("CASE_ALREADY_CLOSED", 409, "case item already closed.");
        }
        String recommendation = str(request, "recommendation");
        if (blank(recommendation)) {
            throw new PctDomainException("RECOMMENDATION_REQUIRED", 400,
                    "an MDT outcome requires a recommendation.");
        }
        List<Map<String, Object>> positions = readPositions(request.get("positions"));
        if (positions.isEmpty()) {
            throw new PctDomainException("POSITIONS_REQUIRED", 400,
                    "consensus recording requires at least one participant position.");
        }
        long agree = 0;
        long dissent = 0;
        for (Map<String, Object> p : positions) {
            String position = String.valueOf(p.getOrDefault("position", "")).trim().toUpperCase(Locale.ROOT);
            if (!POSITIONS.contains(position)) {
                throw new PctDomainException("INVALID_POSITION", 400,
                        "position must be AGREE or DISSENT (was " + position + ").");
            }
            p.put("position", position);
            if ("DISSENT".equals(position)) {
                dissent++;
                String note = String.valueOf(p.getOrDefault("note", "")).trim();
                if (note.isEmpty()) {
                    throw new PctDomainException("DISSENT_NOTE_REQUIRED", 400,
                            "a DISSENT position requires a note — dissent is recorded, never silent.");
                }
            } else {
                agree++;
            }
        }

        item.setRecommendation(recommendation.trim());
        item.setPositions(writeJson(positions));
        item.setRecordedBy(defaulted(str(request, "recorded_by", "recordedBy"), ctx.actorId()));
        item.setStatus("CLOSED");
        item.setClosedAt(OffsetDateTime.now());
        caseItemRepository.save(item);

        // Consensus package back onto the presented referral — the referral stays the clinical SoR.
        Map<String, Object> respond = new LinkedHashMap<>();
        respond.put("response_type", "RESPONSE");
        respond.put("message", "MDT board recommendation (" + agree + " agree, " + dissent + " dissent): "
                + recommendation.trim());
        respond.put("responder_id", defaulted(item.getRecordedBy(), "mdt-board"));
        orchestrationService.respondReferral(item.getReferralId().toString(), respond);

        emit("telemedicine.session.mdt_outcome_recorded", item.getMdtSessionId().toString(), ctx.tenantId(),
                Map.of("caseItemId", item.getCaseItemId().toString(),
                        "referralId", item.getReferralId().toString(),
                        "agree", agree, "dissent", dissent));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseItemId", item.getCaseItemId().toString());
        out.put("status", item.getStatus());
        out.put("agree", agree);
        out.put("dissent", dissent);
        return out;
    }

    /**
     * Board read model with SERVER-SIDE identity visibility. Under PSEUDONYMISED/ANONYMISED, a
     * viewer who is not chair/scribe/presenter sees "Case N" and no patient identifier.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBoard(String mdtSessionId, String viewerId) {
        TrustContext ctx = TrustContextHolder.require();
        MdtSessionEntity session = getSession(ctx, mdtSessionId);
        List<MdtCaseItemEntity> items = caseItemRepository.findByMdtSessionIdOrderByOrdinalAsc(session.getMdtSessionId());

        String viewer = blank(viewerId) ? defaulted(ctx.actorId(), "") : viewerId.trim();
        boolean privileged = viewer.equals(session.getChairId()) || viewer.equals(session.getScribeId());
        boolean pseudonymise = Set.of("PSEUDONYMISED", "ANONYMISED").contains(session.getIdentityVisibility());

        List<Map<String, Object>> agenda = new ArrayList<>();
        for (MdtCaseItemEntity item : items) {
            boolean itemPrivileged = privileged || viewer.equals(item.getPresenterId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseItemId", item.getCaseItemId().toString());
            row.put("ordinal", item.getOrdinal());
            row.put("label", "Case " + item.getOrdinal());
            row.put("status", item.getStatus());
            row.put("recommendation", item.getRecommendation());
            row.put("positions", readPositions(item.getPositions()));
            row.put("recordedBy", item.getRecordedBy());
            if (pseudonymise && !itemPrivileged) {
                // Server-side pseudonymised presentation: no referral id, no patient identifier.
                row.put("referralId", null);
                row.put("patientCpid", null);
                row.put("pseudonymised", true);
            } else {
                row.put("referralId", item.getReferralId().toString());
                row.put("patientCpid", referralRepository
                        .findByTenantIdAndReferralId(ctx.tenantId(), item.getReferralId())
                        .map(ReferralEntity::getPatientCpid).orElse(null));
                row.put("pseudonymised", false);
            }
            agenda.add(row);
        }
        Map<String, Object> out = sessionSummary(session, items.size());
        out.put("agenda", agenda);
        out.put("viewerPrivileged", privileged);
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MdtSessionEntity getSession(TrustContext ctx, String mdtSessionId) {
        return sessionRepository.findByTenantIdAndMdtSessionId(ctx.tenantId(), parseUuid(mdtSessionId))
                .orElseThrow(() -> new PctDomainException("MISSING_MDT_SESSION", 404,
                        "MDT session not found: " + mdtSessionId));
    }

    private Map<String, Object> sessionSummary(MdtSessionEntity session, int caseCount) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mdtSessionId", session.getMdtSessionId().toString());
        out.put("title", session.getTitle());
        out.put("chairId", session.getChairId());
        out.put("scribeId", session.getScribeId());
        out.put("specialty", session.getSpecialty());
        out.put("routingPoolId", session.getRoutingPoolId());
        out.put("identityVisibility", session.getIdentityVisibility());
        out.put("status", session.getStatus());
        out.put("scheduledAt", session.getScheduledAt() == null ? null : session.getScheduledAt().toString());
        out.put("caseCount", caseCount);
        return out;
    }

    private void emit(String eventType, String aggregateId, UUID tenantId, Map<String, Object> payload) {
        String versionedType = TelemedicineEventVersion.withV1(eventType);
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("telemedicine");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(versionedType);
        outbox.setTenantId(tenantId);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", versionedType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("tenantId", tenantId);
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("payload", payload == null ? Map.of() : payload);
        outbox.setPayload(writeJson(envelope));
        outboxRepository.save(outbox);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readPositions(Object raw) {
        if (raw == null) return new ArrayList<>();
        try {
            if (raw instanceof String s) {
                if (s.isBlank()) return new ArrayList<>();
                return objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() { });
            }
            if (raw instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) out.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("MDT positions parse failed: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("MDT payload serialization failed", e);
        }
    }

    private static String str(Map<String, Object> request, String... keys) {
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaulted(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new PctDomainException("INVALID_ID", 400, "not a valid UUID: " + value);
        }
    }
}
