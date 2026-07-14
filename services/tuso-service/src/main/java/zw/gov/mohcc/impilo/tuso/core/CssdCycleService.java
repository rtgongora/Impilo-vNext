package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleStateMachine.Action;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleStateMachine.State;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetCssdCycleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InstrumentSetCssdCycleRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InstrumentSetRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CSSD (Central Sterile Services Department) sterilisation lifecycle service (Wave 3, spec §9).
 *
 * <p>TUSO is the SoR for the instrument-set registry and its sterilisation state. This service is the
 * write path Wave 2 lacked: it issues a STERILE set to a theatre case (→ IN_USE), returns it (→ SOILED),
 * runs a decontamination cycle (→ STERILE with a freshly stamped {@code sterile_until}), and flags a
 * contaminated / incomplete return. Every transition is guarded by {@link CssdCycleStateMachine} and
 * appended to the per-set cycle audit log, and emits an outbox event.</p>
 *
 * <p>inpatient holds only a LINK/PROJECTION (procedure_instrument_set_use) — it never owns set truth.</p>
 */
@Service
public class CssdCycleService {

    private static final Logger log = LoggerFactory.getLogger(CssdCycleService.class);

    private static final int DEFAULT_SHELF_LIFE_DAYS = 180;
    private static final String AGGREGATE = "INSTRUMENT_SET";

    private final InstrumentSetRepository setRepository;
    private final InstrumentSetCssdCycleRepository cycleRepository;
    private final EventOutboxRepository outboxRepository;

    public CssdCycleService(InstrumentSetRepository setRepository,
                            InstrumentSetCssdCycleRepository cycleRepository,
                            EventOutboxRepository outboxRepository) {
        this.setRepository = setRepository;
        this.cycleRepository = cycleRepository;
        this.outboxRepository = outboxRepository;
    }

    /** Issue a STERILE set to a theatre case. STERILE → IN_USE; opens a new cycle (state USE). */
    @Transactional
    public Map<String, Object> issueToCase(UUID setId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        InstrumentSetEntity set = requireSet(tenantId, setId);
        assertSterileNotExpired(set);
        State next = guard(set, Action.ISSUE);

        int cycleNo = (int) cycleRepository.countByTenantIdAndInstrumentSetId(tenantId, setId) + 1;
        InstrumentSetCssdCycleEntity cycle = new InstrumentSetCssdCycleEntity();
        cycle.setTenantId(tenantId);
        cycle.setInstrumentSetId(setId);
        cycle.setCycleNo(cycleNo);
        cycle.setCycleState("USE");
        cycle.setFacilityId(set.getFacilityId());
        cycle.setEpisodeRef(str(body, "episodeRef", "episode_ref"));
        cycle.setIssuedTo(firstNonBlank(str(body, "issuedTo", "issued_to"), actor(ctx)));
        cycle.setIssuedAt(OffsetDateTime.now());
        cycle.setCreatedBy(actor(ctx));
        cycleRepository.save(cycle);

        set.setSterilisationState(next.name()); // IN_USE
        setRepository.save(set);

        emit(tenantId, ctx, setId, "tuso.instrument_set.issued", payload(set, cycle,
                "episodeRef", cycle.getEpisodeRef(), "issuedTo", cycle.getIssuedTo()));
        log.info("TUSO-CSSD: instrument set {} issued to case {} (cycle {})", setId, cycle.getEpisodeRef(), cycleNo);
        return view(set, cycle);
    }

    /** Return a set from a case. IN_USE → SOILED; closes the issue leg of the current cycle. */
    @Transactional
    public Map<String, Object> returnFromCase(UUID setId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        InstrumentSetEntity set = requireSet(tenantId, setId);
        State next = guard(set, Action.RETURN);

        InstrumentSetCssdCycleEntity cycle = requireOpenCycle(tenantId, setId);
        cycle.setReturnedAt(OffsetDateTime.now());
        cycle.setCycleState("SOILED");
        boolean contaminated = flag(body, "contaminated");
        boolean incomplete = flag(body, "incomplete");
        cycle.setContaminated(contaminated);
        cycle.setIncomplete(incomplete);
        String notes = str(body, "notes");
        if (notes != null) cycle.setNotes(notes);
        cycleRepository.save(cycle);

        set.setSterilisationState(next.name()); // SOILED
        setRepository.save(set);

        emit(tenantId, ctx, setId, "tuso.instrument_set.returned", payload(set, cycle,
                "contaminated", contaminated, "incomplete", incomplete));
        log.info("TUSO-CSSD: instrument set {} returned SOILED (contaminated={}, incomplete={})",
                setId, contaminated, incomplete);
        return view(set, cycle);
    }

    /**
     * Run a full CSSD decontamination cycle: SOILED|EXPIRED → REPROCESSING → STERILISED → STERILE,
     * re-stamping {@code sterile_until}. Requires an autoclave reference + operator (two-facts audit).
     */
    @Transactional
    public Map<String, Object> reprocess(UUID setId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        InstrumentSetEntity set = requireSet(tenantId, setId);
        // Validate the entry transition (SOILED|EXPIRED → REPROCESSING) before running the load.
        guard(set, Action.REPROCESS);

        String autoclaveRef = str(body, "autoclaveRef", "autoclave_ref");
        String operator = firstNonBlank(str(body, "operator"), actor(ctx));
        if (autoclaveRef == null || autoclaveRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "autoclaveRef is required to record a CSSD sterilisation cycle");
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sterileUntil = resolveSterileUntil(body, now);

        InstrumentSetCssdCycleEntity cycle = requireOpenCycle(tenantId, setId);
        cycle.setReprocessingAt(now);
        cycle.setSterilisedAt(now);
        cycle.setSterileUntil(sterileUntil);
        cycle.setAutoclaveRef(autoclaveRef);
        cycle.setOperator(operator);
        cycle.setCycleState("STERILE");
        cycleRepository.save(cycle);

        set.setSterilisationState("STERILE");
        set.setSterileUntil(sterileUntil);
        setRepository.save(set);

        emit(tenantId, ctx, setId, "tuso.instrument_set.reprocessed", payload(set, cycle,
                "autoclaveRef", autoclaveRef, "operator", operator,
                "sterileUntil", sterileUntil.toString()));
        log.info("TUSO-CSSD: instrument set {} reprocessed → STERILE (autoclave {}, until {})",
                setId, autoclaveRef, sterileUntil);
        return view(set, cycle);
    }

    /**
     * Flag a set contaminated / incomplete outside the normal return path (e.g. an audit finding).
     * Pulls a STERILE/STERILISED set back to SOILED so it must be reprocessed before re-issue.
     */
    @Transactional
    public Map<String, Object> flagContaminated(UUID setId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        InstrumentSetEntity set = requireSet(tenantId, setId);
        InstrumentSetCssdCycleEntity cycle = requireOpenCycle(tenantId, setId);
        boolean contaminated = flag(body, "contaminated");
        boolean incomplete = flag(body, "incomplete");
        cycle.setContaminated(contaminated || cycle.isContaminated());
        cycle.setIncomplete(incomplete || cycle.isIncomplete());
        String notes = str(body, "notes");
        if (notes != null) cycle.setNotes(notes);
        // A contaminated set in circulation must be pulled to SOILED (reprocessing required).
        String current = set.getSterilisationState();
        if ("STERILE".equals(current) || "STERILISED".equals(current)) {
            set.setSterilisationState("SOILED");
            cycle.setCycleState("SOILED");
        }
        cycleRepository.save(cycle);
        setRepository.save(set);
        emit(tenantId, ctx, setId, "tuso.instrument_set.flagged", payload(set, cycle,
                "contaminated", cycle.isContaminated(), "incomplete", cycle.isIncomplete()));
        return view(set, cycle);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSet(UUID setId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        InstrumentSetEntity set = requireSet(tenantId, setId);
        InstrumentSetCssdCycleEntity cycle = cycleRepository
                .findFirstByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(tenantId, setId).orElse(null);
        return view(set, cycle);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCycles(UUID setId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        requireSet(tenantId, setId);
        return cycleRepository.findByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(tenantId, setId)
                .stream().map(InstrumentSetCssdCycleEntity::toMap).toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────
    private State guard(InstrumentSetEntity set, Action action) {
        try {
            return CssdCycleStateMachine.transition(CssdCycleStateMachine.parse(set.getSterilisationState()), action);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private void assertSterileNotExpired(InstrumentSetEntity set) {
        if (set.getSterileUntil() != null && set.getSterileUntil().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Instrument set sterility expired at " + set.getSterileUntil() + " — reprocess before issue");
        }
    }

    private InstrumentSetEntity requireSet(UUID tenantId, UUID setId) {
        return setRepository.findByTenantIdAndId(tenantId, setId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument set not found"));
    }

    private InstrumentSetCssdCycleEntity requireOpenCycle(UUID tenantId, UUID setId) {
        return cycleRepository.findFirstByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(tenantId, setId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No CSSD cycle open for this set (issue it to a case first)"));
    }

    private OffsetDateTime resolveSterileUntil(Map<String, Object> body, OffsetDateTime now) {
        String explicit = str(body, "sterileUntil", "sterile_until");
        if (explicit != null && !explicit.isBlank()) {
            try {
                return OffsetDateTime.parse(explicit);
            } catch (RuntimeException ignored) {
                // fall through to shelf-life computation
            }
        }
        int days = DEFAULT_SHELF_LIFE_DAYS;
        Object raw = body != null ? firstNonNull(body.get("shelfLifeDays"), body.get("shelf_life_days")) : null;
        if (raw != null) {
            try {
                days = Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) { /* keep default */ }
        }
        return now.plusDays(days);
    }

    private Map<String, Object> view(InstrumentSetEntity set, InstrumentSetCssdCycleEntity cycle) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", set.getId() != null ? set.getId().toString() : null);
        out.put("name", set.getName());
        out.put("facilityId", set.getFacilityId());
        out.put("sterilisationState", set.getSterilisationState());
        out.put("sterileUntil", set.getSterileUntil() != null ? set.getSterileUntil().toString() : null);
        out.put("cycle", cycle != null ? cycle.toMap() : null);
        return out;
    }

    private Map<String, Object> payload(InstrumentSetEntity set, InstrumentSetCssdCycleEntity cycle,
                                        Object... kv) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("instrumentSetId", set.getId() != null ? set.getId().toString() : null);
        p.put("name", set.getName());
        p.put("facilityId", set.getFacilityId());
        p.put("sterilisationState", set.getSterilisationState());
        p.put("cycleNo", cycle != null ? cycle.getCycleNo() : null);
        p.put("cycleState", cycle != null ? cycle.getCycleState() : null);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return p;
    }

    private void emit(UUID tenantId, TrustContext ctx, UUID setId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(AGGREGATE);
        event.setAggregateId(setId.toString());
        event.setEventType(eventType);
        event.setTenantId(tenantId.toString());
        event.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "national-spine");
        event.setSubjectType(AGGREGATE);
        event.setSubjectId(setId.toString());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
        event.setIdempotencyKey("tuso:cssd:" + setId + ":" + eventType + ":" + UUID.randomUUID());
        event.setSchemaVersion(1);
        event.setOccurredAt(OffsetDateTime.now());
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private static String actor(TrustContext ctx) {
        try {
            String a = ctx.actorId();
            return a != null && !a.isBlank() ? a : "system";
        } catch (RuntimeException e) {
            return "system";
        }
    }

    private static boolean flag(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }
}
