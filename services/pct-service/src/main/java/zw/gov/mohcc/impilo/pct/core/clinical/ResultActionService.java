package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ResultActionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ResultActionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What was DONE about a diagnostic result — the second half of §25's "results are reviewed and
 * actioned".
 *
 * <p><strong>The first half already exists and is not rebuilt here.</strong> OROS owns the order,
 * the result and acknowledgement, including escalation for an unacknowledged critical result. Its
 * acknowledgement genuinely works. What nothing recorded was what the clinician did next: a
 * potassium of 6.8 can be seen, ticked, and changed nothing, and until now the record could not tell
 * that apart from one that was acted on.</p>
 *
 * <p><strong>The rule that carries the weight: NO_ACTION_NEEDED must justify itself.</strong> It is
 * the reassuring answer, so it is the one an audit later reads as "reviewed and safely closed".
 * Every other action names itself; this one has to say why.</p>
 */
@Service
public class ResultActionService {

    private static final Logger log = LoggerFactory.getLogger(ResultActionService.class);

    public static final String NO_ACTION_NEEDED = "NO_ACTION_NEEDED";

    /** Mirrors the CHECK in V115. */
    public static final Set<String> ACTIONS = new LinkedHashSet<>(List.of(
            NO_ACTION_NEEDED,
            "TREATMENT_STARTED", "TREATMENT_CHANGED", "TREATMENT_STOPPED",
            "REPEAT_REQUESTED", "FURTHER_TEST_REQUESTED", "REFERRED",
            "PROBLEM_ADDED", "PROBLEM_RESOLVED", "ESCALATED",
            "PATIENT_INFORMED", "AWAITING_DISCUSSION"));

    /** Actions that assert something about the problem list and must therefore name a problem. */
    public static final Set<String> ACTIONS_REQUIRING_PROBLEM =
            Set.of("PROBLEM_ADDED", "PROBLEM_RESOLVED");

    private final ResultActionRepository repository;
    private final ProblemRepository problemRepository;
    private final ClinicalAccessGuard accessGuard;

    public ResultActionService(ResultActionRepository repository,
                               ProblemRepository problemRepository,
                               ClinicalAccessGuard accessGuard) {
        this.repository = repository;
        this.problemRepository = problemRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public ResultActionEntity record(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subjectCpid = required(body, "subject_cpid", "subjectCpid");
        String journeyRaw = str(body.get("journey_id"), body.get("journeyId"));
        String encounterRaw = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subjectCpid, journeyRaw, encounterRaw);

        if (journeyRaw == null && encounterRaw == null) {
            throw new IllegalArgumentException(
                    "A result action must carry a journey_id or an encounter_id "
                    + "(care-continuum-doctrine CC-5).");
        }

        String resultRef = required(body, "result_ref", "resultRef");
        String action = ProblemVocabulary.require("action", str(body.get("action")), ACTIONS, true);
        String rationale = str(body.get("rationale"));

        if (NO_ACTION_NEEDED.equals(action) && (rationale == null || rationale.isBlank())) {
            throw new IllegalArgumentException(
                    "Recording that no action was needed requires a reason. It is the reassuring "
                    + "answer, so it is the one an audit later reads as reviewed and safely closed — "
                    + "every other action names itself, this one has to justify itself.");
        }

        UUID problemId = uuid(str(body.get("problem_id"), body.get("problemId")));
        if (ACTIONS_REQUIRING_PROBLEM.contains(action) && problemId == null) {
            throw new IllegalArgumentException(
                    "Action " + action + " asserts something about the problem list and must name "
                    + "which problem, or the claim cannot be followed back to it.");
        }
        if (problemId != null) {
            var problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));
            if (!problem.getTenantId().equals(ctx.tenantId())
                || !problem.getSubjectCpid().equals(subjectCpid)) {
                throw new IllegalArgumentException(
                        "Problem " + problemId + " does not belong to patient " + subjectCpid);
            }
        }

        ResultActionEntity e = new ResultActionEntity();
        e.setActionId(UUID.randomUUID());
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setJourneyId(uuid(journeyRaw));
        e.setEncounterId(uuid(encounterRaw));
        e.setResultRef(resultRef);
        e.setOrderRef(str(body.get("order_ref"), body.get("orderRef")));
        e.setAction(action);
        e.setRationale(rationale);
        e.setProblemId(problemId);
        e.setActionedBy(ctx.actorId());
        repository.save(e);

        log.info("pct.result.actioned id={} subject={} result={} action={} by={} correlationId={}",
                e.getActionId(), subjectCpid, resultRef, action, ctx.actorId(), ctx.correlationId());
        return e;
    }

    @Transactional(readOnly = true)
    public List<ResultActionEntity> listForSubject(String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return repository.findByTenantIdAndSubjectCpidOrderByActionedAtDesc(ctx.tenantId(), subjectCpid);
    }

    /**
     * Given the results a caller knows about, say which have been actioned and which have not.
     *
     * <p>This is the read §25 needs and the one OROS cannot produce, because OROS knows a result was
     * acknowledged and not what it caused. <strong>The caller supplies the result set.</strong> PCT
     * has no list of this patient's results and must not invent one: reporting only on results it
     * happens to have actions for would make the unactioned ones — the entire point of the read —
     * invisible.</p>
     *
     * @param resultRefs the results in scope, from OROS. An empty set answers about nothing and says so.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> actionStatus(Collection<String> resultRefs) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> out = new LinkedHashMap<>();

        if (resultRefs == null || resultRefs.isEmpty()) {
            out.put("actioned", List.of());
            out.put("not_actioned", List.of());
            out.put("scope_note",
                    "No results were supplied, so this answers about nothing. PCT does not hold the "
                    + "result list — OROS does — and an empty answer here is not a statement that "
                    + "every result has been actioned.");
            return out;
        }

        List<ResultActionEntity> actions =
                repository.findByTenantIdAndResultRefIn(ctx.tenantId(), resultRefs);
        Set<String> actioned = new LinkedHashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ResultActionEntity a : actions) {
            actioned.add(a.getResultRef());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("result_ref", a.getResultRef());
            row.put("action", a.getAction());
            row.put("rationale", a.getRationale());
            row.put("actioned_by", a.getActionedBy());
            row.put("actioned_at", a.getActionedAt());
            rows.add(row);
        }

        List<String> notActioned = new ArrayList<>();
        for (String ref : resultRefs) {
            if (!actioned.contains(ref)) {
                notActioned.add(ref);
            }
        }

        out.put("actioned", rows);
        out.put("not_actioned", notActioned);
        out.put("scope_note",
                notActioned.size() + " of " + resultRefs.size() + " supplied result(s) have no "
                + "recorded action. Acknowledged is not actioned: a result can be seen, ticked and "
                + "change nothing, and OROS's acknowledgement cannot tell those apart.");
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static String required(Map<String, Object> body, String snake, String camel) {
        String v = str(body.get(snake), body.get(camel));
        if (v == null) {
            throw new IllegalArgumentException("Missing required field: " + snake);
        }
        return v;
    }

    private static String str(Object... candidates) {
        for (Object o : candidates) {
            if (o != null && !o.toString().isBlank()) {
                return o.toString().trim();
            }
        }
        return null;
    }

    private static UUID uuid(String raw) {
        return raw == null ? null : UUID.fromString(raw);
    }
}
