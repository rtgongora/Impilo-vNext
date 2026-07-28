package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Outpatient problems-list management (PCT).
 *
 * <p>PCT is the SoR for the outpatient encounter; the problems list attaches to a subject (and optionally a
 * journey/encounter). Authorization is enforced upstream at Envoy ext_authz (policy {@code CARE-PLAN-WRITE}
 * family / track P).</p>
 */
@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public ProblemService(ProblemRepository problemRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper,
                          ClinicalAccessGuard accessGuard) {
        this.problemRepository = problemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<ProblemEntity> list(String subjectCpid, String clinicalStatus) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (clinicalStatus == null || clinicalStatus.isBlank()) {
            return problemRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenantId, subjectCpid);
        }
        return problemRepository.findByTenantIdAndSubjectCpidAndClinicalStatusOrderByCreatedAtDesc(
                tenantId, subjectCpid, clinicalStatus.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * How the caller has resolved a duplicate this service reported. Absent means they have not
     * been told about one yet.
     */
    private static final String RESOLUTION_SAME_PROBLEM_RETURNING = "SAME_PROBLEM_RETURNING";
    private static final String RESOLUTION_DISTINCT_PROBLEM = "DISTINCT_PROBLEM";

    @Transactional
    public ProblemEntity add(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ProblemEntity p = new ProblemEntity();
        p.setTenantId(ctx.tenantId());
        p.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        p.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        p.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        // Subject-relationship gate (dimension 6): the actor must hold an active care context for
        // this patient. ext_authz enforces RBAC but cannot bind the body subject to consent/relationship.
        accessGuard.requireCareRelationship(ctx, p.getSubjectCpid(), p.getJourneyId(), p.getEncounterId());
        p.setCode(str(body.get("code")));
        p.setCodeSystem(str(body.get("code_system"), body.get("codeSystem")));
        p.setDisplay(required(body, "display", "display"));
        p.setCategory(ProblemVocabulary.require("category",
                body.getOrDefault("category", "DIAGNOSIS"), ProblemVocabulary.CATEGORIES, true));
        p.setClinicalStatus(ProblemVocabulary.require("clinical_status",
                body.getOrDefault("clinical_status", "ACTIVE"), ProblemVocabulary.CLINICAL_STATUSES, true));
        // No default on either of these: an unstated severity or certainty stays NULL. Defaulting
        // would record an assessment the clinician never made — "mild" is the one severity that
        // stops a reader looking, and CONFIRMED is the one certainty that ends an investigation.
        p.setSeverity(upper(str(body.get("severity"))));
        p.setDiagnosticCertainty(ProblemVocabulary.require("diagnostic_certainty",
                str(body.get("diagnostic_certainty"), body.get("diagnosticCertainty")),
                ProblemVocabulary.CERTAINTIES, false));
        String onset = str(body.get("onset_date"), body.get("onsetDate"));
        if (onset != null) {
            p.setOnsetDate(LocalDate.parse(onset));
        }
        String reviewDate = str(body.get("review_date"), body.get("reviewDate"));
        if (reviewDate != null) {
            p.setReviewDate(LocalDate.parse(reviewDate));
        }
        p.setEvidence(str(body.get("evidence")));
        p.setResponsibleService(str(body.get("responsible_service"), body.get("responsibleService")));
        // A problem recorded directly as recurrent is one the clinician already knows has come back.
        if (ProblemVocabulary.RECURRENT_STATUSES.contains(p.getClinicalStatus())) {
            p.setLastRecurrenceAt(OffsetDateTime.now());
        }
        p.setNotes(str(body.get("notes")));
        p.setRecordedBy(ctx.actorId());

        // Cross-clinic duplication check. A patient seen in the diabetic clinic, the renal clinic
        // and a medical ward acquires the same diagnosis three times unless something looks.
        String resolution = upper(str(body.get("duplicate_resolution"), body.get("duplicateResolution")));
        if (!RESOLUTION_DISTINCT_PROBLEM.equals(resolution)) {
            ProblemEntity existing = findDuplicate(ctx.tenantId(), p);
            if (existing != null) {
                if (RESOLUTION_SAME_PROBLEM_RETURNING.equals(resolution)) {
                    // Not a new row: the disease already on the list has come back.
                    return changeStatus(existing.getProblemId(), "RECURRENCE");
                }
                throw new DuplicateProblemException(existing);
            }
        }

        p = problemRepository.save(p);

        emit("PROBLEM_ADDED", p);
        log.info("pct.problem.added id={} subject={} status={} by={} correlationId={}",
                p.getProblemId(), p.getSubjectCpid(), p.getClinicalStatus(), ctx.actorId(), ctx.correlationId());
        return p;
    }

    @Transactional
    public ProblemEntity resolve(UUID problemId) {
        return changeStatus(problemId, "RESOLVED");
    }

    /**
     * Moves a problem to a new clinical status, keeping one disease as one row.
     *
     * <p>The transition that matters is back out of RESOLVED. Before this existed the only ways to
     * record a condition returning were to edit the resolved row — losing the fact that it had ever
     * resolved — or to add a second problem, which is how one disease becomes two entries and how a
     * problem list stops being countable. A recurrence now stamps {@code last_recurrence_at} and
     * clears the stale resolution, and the transition itself is preserved on the event, which
     * carries the status the problem moved from.</p>
     */
    @Transactional
    public ProblemEntity changeStatus(UUID problemId, String requestedStatus) {
        TrustContext ctx = TrustContextHolder.require();
        ProblemEntity p = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));
        if (!p.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Problem not found: " + problemId);
        }
        String status = ProblemVocabulary.require(
                "clinical_status", requestedStatus, ProblemVocabulary.CLINICAL_STATUSES, true);
        String previousStatus = p.getClinicalStatus();

        p.setClinicalStatus(status);
        if ("RESOLVED".equals(status)) {
            p.setResolvedAt(OffsetDateTime.now());
        } else {
            // No longer resolved, so a resolution timestamp left behind would misdate the record.
            p.setResolvedAt(null);
        }
        if (ProblemVocabulary.RECURRENT_STATUSES.contains(status)) {
            p.setLastRecurrenceAt(OffsetDateTime.now());
        }
        p = problemRepository.save(p);

        emit("RESOLVED".equals(status) ? "PROBLEM_RESOLVED" : "PROBLEM_STATUS_CHANGED", p, previousStatus);
        log.info("pct.problem.status_changed id={} subject={} from={} to={} by={} correlationId={}",
                p.getProblemId(), p.getSubjectCpid(), previousStatus, status,
                ctx.actorId(), ctx.correlationId());
        return p;
    }

    /**
     * Finds an open problem on this patient's list that the incoming one appears to duplicate, or
     * null.
     *
     * <p>Two problems match if they carry the same code in the same code system, or — when either
     * is uncoded — the same normalised display. Coded matching is the reliable half; display
     * matching exists because most problems in this estate are still recorded as free text, and a
     * duplicate detector that only worked on coded rows would fire almost never.</p>
     *
     * <p><strong>Only open problems are compared.</strong> A resolved problem is not a duplicate of
     * a new one — a second episode of pneumonia two years later is a new problem, and treating the
     * old resolved row as a collision would make it unrecordable. Recurrence is the caller's
     * explicit choice, not this method's guess. REFUTED problems are likewise skipped: a diagnosis
     * that was excluded must not block someone from raising it again on new evidence.</p>
     */
    private ProblemEntity findDuplicate(UUID tenantId, ProblemEntity incoming) {
        List<ProblemEntity> existing =
                problemRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(
                        tenantId, incoming.getSubjectCpid());
        for (ProblemEntity candidate : existing) {
            if (!ProblemVocabulary.OPEN_STATUSES.contains(candidate.getClinicalStatus())) {
                continue;
            }
            if ("REFUTED".equals(candidate.getDiagnosticCertainty())) {
                continue;
            }
            if (sameCode(candidate, incoming) || sameDisplay(candidate, incoming)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean sameCode(ProblemEntity a, ProblemEntity b) {
        return a.getCode() != null && b.getCode() != null
                && a.getCode().equalsIgnoreCase(b.getCode())
                && Objects.equals(
                        a.getCodeSystem() == null ? null : a.getCodeSystem().toUpperCase(Locale.ROOT),
                        b.getCodeSystem() == null ? null : b.getCodeSystem().toUpperCase(Locale.ROOT));
    }

    private static boolean sameDisplay(ProblemEntity a, ProblemEntity b) {
        return normaliseDisplay(a.getDisplay()).equals(normaliseDisplay(b.getDisplay()));
    }

    /** Case and internal whitespace are not clinical differences. */
    private static String normaliseDisplay(String display) {
        if (display == null) return "";
        return display.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void emit(String eventType, ProblemEntity p) {
        emit(eventType, p, null);
    }

    /**
     * @param previousStatus the status moved from, or null on creation. The transition is only
     *                       recoverable from the event stream, so it has to travel on the event.
     */
    private void emit(String eventType, ProblemEntity p, String previousStatus) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("problemId", p.getProblemId().toString());
        payload.put("subjectCpid", p.getSubjectCpid());
        payload.put("journeyId", p.getJourneyId());
        // The coded value travels because BUTANO archives this as a FHIR Condition, and a Condition
        // carrying only a display string is not a diagnosis anyone can query, map or reconcile.
        // Deliberately NOT carried: notes. Free text is the one field that can hold PII, and the
        // SHR boundary rule is enforced by never putting it on the wire in the first place.
        payload.put("code", p.getCode());
        payload.put("codeSystem", p.getCodeSystem());
        payload.put("display", p.getDisplay());
        payload.put("onsetDate", p.getOnsetDate() == null ? null : p.getOnsetDate().toString());
        payload.put("clinicalStatus", p.getClinicalStatus());
        payload.put("previousClinicalStatus", previousStatus);
        payload.put("diagnosticCertainty", p.getDiagnosticCertainty());
        payload.put("category", p.getCategory());
        payload.put("actorId", ctx.actorId());
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("PROBLEM");
        outbox.setAggregateId(p.getProblemId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    /** Required field, readable in either spelling; names the snake_case form when absent. */
    private static String required(Map<String, Object> body, String snakeKey, String camelKey) {
        String v = str(body.get(snakeKey), body.get(camelKey));
        if (v == null) {
            throw new IllegalArgumentException("Missing field: " + snakeKey);
        }
        return v;
    }

    /**
     * First non-blank of the candidates.
     *
     * <p>Variadic so every multi-word key can be read in both spellings. Required fields fail
     * loudly on the wrong spelling, but an OPTIONAL field read snake-only would simply be absent —
     * a 201 with the clinical value silently null. That is the failure mode this service must not
     * have, because the emergency lane's handover contract requires diagnostic certainty to
     * travel, and a certainty that vanished in transit is indistinguishable from one nobody
     * stated. Matches the pattern already used in MaternityService.</p>
     */
    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private static String upperOr(Object v, String def) {
        String s = str(v);
        return s == null ? def : s.toUpperCase(Locale.ROOT);
    }

    /** Normalises case without inventing a value — absent stays absent. */
    private static String upper(Object v) {
        String s = str(v);
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise problem payload: {}", e.getMessage());
            return "{}";
        }
    }
}
