package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.assistant.ClinicalAssistantService;
import zw.gov.mohcc.impilo.clinical.cds.CdsInsightService;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.interpretation.InterpretationEvaluationService;
import zw.gov.mohcc.impilo.clinical.nudge.NudgeEvaluationService;
import zw.gov.mohcc.impilo.clinical.pathway.PathwaySessionService;
import zw.gov.mohcc.impilo.clinical.persistence.entity.OverrideRecordEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwaySessionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.OverrideRecordRepository;
import zw.gov.mohcc.impilo.clinical.prescribing.PrescribingEvaluationService;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalContextEnricher;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API surface — Experience BFF and trusted back-office proxies only.
 */
@RestController
@RequestMapping("/internal/v1/clinical")
public class ClinicalKnowledgePlatformController {

    private final ClinicalAssistantService assistantService;
    private final PrescribingEvaluationService prescribingEvaluationService;
    private final PathwaySessionService pathwaySessionService;
    private final NudgeEvaluationService nudgeEvaluationService;
    private final TraceService traceService;
    private final OverrideRecordRepository overrideRecordRepository;
    private final ClinicalRulesEngine clinicalRulesEngine;
    private final ClinicalContextEnricher clinicalContextEnricher;
    private final ClinicalOutboxWriter clinicalOutboxWriter;
    private final CdsInsightService cdsInsightService;
    private final InterpretationEvaluationService interpretationEvaluationService;
    private final zw.gov.mohcc.impilo.clinical.rules.RuleGovernanceService ruleGovernanceService;

    /**
     * Paediatric danger-sign evaluation. Field-injected so the shared constructor stays a
     * stable merge surface, matching the convention used elsewhere in the clinical plane.
     */
    @org.springframework.beans.factory.annotation.Autowired
    private zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService dangerSignEvaluationService;

    /** IMNCI assess-and-classify. Field-injected for the same reason as the danger-sign service. */
    @org.springframework.beans.factory.annotation.Autowired
    private zw.gov.mohcc.impilo.clinical.classification.ImnciClassificationService imnciClassificationService;

    /** EPI immunisation forecasting. Field-injected on the same convention. */
    @org.springframework.beans.factory.annotation.Autowired
    private zw.gov.mohcc.impilo.clinical.immunisation.EpiForecastService epiForecastService;

    /** Growth trajectory interpretation. Field-injected on the same convention. */
    @org.springframework.beans.factory.annotation.Autowired
    private zw.gov.mohcc.impilo.clinical.growth.GrowthIntelligenceService growthIntelligenceService;

    /** IMAM programme knowledge — admission routing and discharge readiness. Same convention. */
    @org.springframework.beans.factory.annotation.Autowired
    private zw.gov.mohcc.impilo.clinical.imam.ImamProgrammeService imamProgrammeService;

    public ClinicalKnowledgePlatformController(
            ClinicalAssistantService assistantService,
            PrescribingEvaluationService prescribingEvaluationService,
            PathwaySessionService pathwaySessionService,
            NudgeEvaluationService nudgeEvaluationService,
            TraceService traceService,
            OverrideRecordRepository overrideRecordRepository,
            ClinicalRulesEngine clinicalRulesEngine,
            ClinicalContextEnricher clinicalContextEnricher,
            ClinicalOutboxWriter clinicalOutboxWriter,
            CdsInsightService cdsInsightService,
            InterpretationEvaluationService interpretationEvaluationService,
            zw.gov.mohcc.impilo.clinical.rules.RuleGovernanceService ruleGovernanceService) {
        this.assistantService = assistantService;
        this.prescribingEvaluationService = prescribingEvaluationService;
        this.pathwaySessionService = pathwaySessionService;
        this.nudgeEvaluationService = nudgeEvaluationService;
        this.traceService = traceService;
        this.overrideRecordRepository = overrideRecordRepository;
        this.clinicalRulesEngine = clinicalRulesEngine;
        this.clinicalContextEnricher = clinicalContextEnricher;
        this.clinicalOutboxWriter = clinicalOutboxWriter;
        this.cdsInsightService = cdsInsightService;
        this.interpretationEvaluationService = interpretationEvaluationService;
        this.ruleGovernanceService = ruleGovernanceService;
    }

    @PostMapping("/assistant/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestHeader(value = "x-tenant-id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {

        String question = body.get("question") != null ? body.get("question").toString() : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> patientContext = body.get("patient_context") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        boolean citizenMode = Boolean.TRUE.equals(body.get("citizen_mode"));
        String role = body.getOrDefault("role", "PROVIDER").toString();

        Map<String, Object> data = assistantService.ask(tenantId, actorId, role, question, patientContext, encounterId, citizenMode);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/assistant/traces/{id}")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable UUID id) {
        JsonNode node = traceService.findTraceJson(id);
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", node));
    }

    @PostMapping("/prescribing/evaluate")
    public ResponseEntity<Map<String, Object>> prescribing(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", prescribingEvaluationService.evaluate(body)));
    }

    @PostMapping("/rules/evaluate")
    public ResponseEntity<Map<String, Object>> rules(@RequestBody Map<String, Object> body) {
        var ctx = clinicalContextEnricher.enrich(ClinicalEvaluationContext.fromMap(body));
        // OF-B3: deterministic engine output governed by clinical.rule_definitions
        // (severity/interruptive overrides + effective-window retirement).
        var alerts = ruleGovernanceService.apply(clinicalRulesEngine.evaluate(ctx))
                .stream().map(RuleAlert::toMap).toList();
        return ResponseEntity.ok(Map.of("data", Map.of("alerts", alerts)));
    }

    /**
     * Context-aware interpretation of vitals/labs against patient-appropriate reference intervals, plus
     * the deterministic rules that fire on the interpreted picture. Auditable + advisory.
     */
    /**
     * Paediatric danger signs — the ETAT emergency signs, the IMNCI general danger signs, and
     * the young-infant signs of possible serious bacterial infection.
     *
     * <p>The response separates what fired from what was never looked at: a result with
     * unassessed signs is marked incomplete, because a clean answer built on unasked questions
     * is the most dangerous output this endpoint could give.</p>
     */
    @PostMapping("/paediatric/danger-signs/evaluate")
    public ResponseEntity<Map<String, Object>> paediatricDangerSigns(@RequestBody Map<String, Object> body) {
        Integer ageDays = body.get("ageDays") instanceof Number n ? n.intValue() : null;
        if (ageDays == null && body.get("age_days") instanceof Number n2) {
            ageDays = n2.intValue();
        }
        var assessment = dangerSignEvaluationService.evaluate(ageDays, body);
        return ResponseEntity.ok(Map.of("data", assessment.toMap()));
    }

    /**
     * Runs the IMNCI assess-and-classify tables for a sick child aged 2 months up to 5 years.
     *
     * <p>Returns one outcome per table, always including the tables that did not apply, so the
     * response shows what was considered rather than only what was found. A classification that
     * could not be safely reached is reported as indeterminate with the observation needed to
     * resolve it — never as the reassuring row.</p>
     *
     * <p>The response carries no diagnosis. IMNCI classifications are management categories, and
     * recording one as a diagnosis would assert something no clinician stated.</p>
     */
    @PostMapping("/paediatric/imnci/classify")
    public ResponseEntity<Map<String, Object>> imnciClassify(@RequestBody Map<String, Object> body) {
        Integer ageDays = body.get("ageDays") instanceof Number n ? n.intValue() : null;
        if (ageDays == null && body.get("age_days") instanceof Number n2) {
            ageDays = n2.intValue();
        }
        var assessment = imnciClassificationService.evaluate(ageDays, body);

        List<Map<String, Object>> classifications = new ArrayList<>();
        for (var o : assessment.classifications()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("table_id", o.tableId());
            m.put("axis", o.axis());
            m.put("status", o.status().name());
            m.put("classification", o.classificationCode());
            m.put("classification_name", o.classificationName());
            m.put("colour", o.colour());
            m.put("provisional", o.provisional());
            m.put("actionable", o.actionable());
            m.put("unresolved_tiers", o.unresolvedTiers());
            m.put("missing_inputs", o.missingInputs());
            m.put("waived_criteria", o.waivedCriteria());
            m.put("action", o.action());
            m.put("treatments", o.treatments());
            m.put("referral_required", o.referralRequired());
            m.put("urgent_referral", o.urgentReferral());
            m.put("rationale", o.rationale());
            classifications.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("age_days", assessment.ageDays());
        // Which IMNCI chart was used. A caller must be able to see that a 10-day-old was assessed
        // on the young-infant chart and not the child one.
        data.put("pathway", assessment.pathway());
        data.put("applicable", assessment.applicable());
        data.put("classifications", classifications);
        data.put("urgent_referral_required", assessment.urgentReferralRequired());
        data.put("referral_required", assessment.referralRequired());
        data.put("incomplete", assessment.incomplete());
        data.put("outstanding_assessments", assessment.outstandingAssessments());
        data.put("unavailable_criteria", assessment.unavailableCriteria());
        data.put("treatment_plan", assessment.treatmentPlan());
        data.put("disposition", assessment.disposition());
        data.put("content_version", assessment.contentVersion());
        data.put("content_source", assessment.contentSource());
        data.put("approval_status", assessment.approvalStatus());
        data.put("note", assessment.note());
        return ResponseEntity.ok(Map.of("data", data));
    }

    /**
     * Forecasts the child's immunisation schedule from their date of birth and the doses already
     * recorded against them.
     *
     * <p>Stateless by design: the dose history is clinical record and belongs to pct-service, so
     * the caller supplies it. The response states, per dose, not only whether it is due but why —
     * a dose can be withheld because the child is too young, because the minimum interval since the
     * previous dose has not elapsed, or because the window has closed for safety, and those need
     * different actions from the health worker.</p>
     */
    @PostMapping("/paediatric/immunisation/forecast")
    public ResponseEntity<Map<String, Object>> immunisationForecast(@RequestBody Map<String, Object> body) {
        Object dobRaw = body.get("date_of_birth") != null ? body.get("date_of_birth") : body.get("dateOfBirth");
        if (dobRaw == null) {
            // Without a date of birth every age and interval gate is unresolvable. Guessing would
            // produce a forecast that looks authoritative and is arbitrary.
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "date_of_birth_required",
                    "message", "An immunisation forecast is entirely a function of age and interval; "
                               + "without a date of birth no dose can be scheduled."));
        }
        java.time.LocalDate dob = java.time.LocalDate.parse(String.valueOf(dobRaw).substring(0, 10));
        Object asOfRaw = body.get("as_of") != null ? body.get("as_of") : body.get("asOf");
        java.time.LocalDate asOf = asOfRaw == null ? java.time.LocalDate.now()
                : java.time.LocalDate.parse(String.valueOf(asOfRaw).substring(0, 10));
        String sex = body.get("sex") != null ? String.valueOf(body.get("sex")) : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doses = body.get("doses") instanceof List<?> l
                ? (List<Map<String, Object>>) (List<?>) l : List.of();

        var forecast = epiForecastService.forecast(dob, sex, doses, asOf);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (var r : forecast.rows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("series", r.series());
            m.put("antigen", r.antigenName());
            m.put("dose_number", r.doseNumber());
            m.put("status", r.status().name());
            m.put("actionable_today", r.actionableToday());
            m.put("earliest_date", String.valueOf(r.earliestDate()));
            m.put("recommended_date", String.valueOf(r.recommendedDate()));
            m.put("overdue_after", r.overdueAfter() == null ? null : String.valueOf(r.overdueAfter()));
            m.put("latest_useful_date", r.latestUsefulDate() == null ? null : String.valueOf(r.latestUsefulDate()));
            m.put("days_overdue", r.daysOverdue());
            m.put("given_on", r.givenOn() == null ? null : String.valueOf(r.givenOn()));
            m.put("rationale", r.rationale());
            rows.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date_of_birth", String.valueOf(forecast.dateOfBirth()));
        data.put("as_of", String.valueOf(forecast.asOf()));
        data.put("age_days", forecast.ageDays());
        data.put("doses", rows);
        data.put("due_now", forecast.dueNow().stream()
                .map(r -> r.series() + " dose " + r.doseNumber()).toList());
        data.put("any_overdue", forecast.anyOverdue());
        data.put("provisional", forecast.provisional());
        data.put("provisional_reasons", forecast.provisionalReasons());
        data.put("schedule_id", forecast.scheduleId());
        data.put("schedule_version", forecast.scheduleVersion());
        data.put("approval_status", forecast.approvalStatus());
        data.put("note", forecast.note());
        return ResponseEntity.ok(Map.of("data", data));
    }

    /**
     * Interprets a child's growth trajectory and says what to do about it.
     *
     * <p>Stateless: pct-service owns the growth history and the z-scores stamped at the time of
     * measurement, and passes them in. This endpoint never recomputes a score, so its
     * interpretation cannot drift away from what the record says.</p>
     *
     * <p>Two measurements are the minimum. A single contact returns not-assessable with the reason,
     * rather than "no concerns" — a child weighed once has not been shown to be growing well.</p>
     */
    @PostMapping("/paediatric/growth/interpret")
    public ResponseEntity<Map<String, Object>> growthInterpret(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = body.get("measurements") instanceof List<?> l
                ? (List<Map<String, Object>>) (List<?>) l
                : body.get("history") instanceof List<?> h
                        ? (List<Map<String, Object>>) (List<?>) h
                        : List.of();
        var assessment = growthIntelligenceService.assess(history);
        return ResponseEntity.ok(Map.of("data",
                zw.gov.mohcc.impilo.clinical.growth.GrowthIntelligenceService.toMap(assessment)));
    }

    /**
     * Routes a malnourished child to a treatment programme: inpatient stabilisation, outpatient
     * therapeutic care, or supplementary feeding.
     *
     * <p>Accepts an IMNCI acute-malnutrition classification directly, which is the point of it. The
     * classification engine already tells the clinician to "enrol in outpatient therapeutic care";
     * this turns that sentence into a programme, a review interval and a ration, so the instruction
     * the chart issues is one the system can actually carry out.</p>
     *
     * <p>A child with nothing measured is reported as not assessable with the reason, never as not
     * eligible — acute malnutrition cannot be ruled out on an unmeasured arm.</p>
     */
    @PostMapping("/paediatric/imam/eligibility")
    public ResponseEntity<Map<String, Object>> imamEligibility(@RequestBody Map<String, Object> body) {
        var eligibility = imamProgrammeService.eligibility(body);
        return ResponseEntity.ok(Map.of("data",
                zw.gov.mohcc.impilo.clinical.imam.ImamProgrammeService.toMap(eligibility)));
    }

    /**
     * Evaluates an open IMAM episode: discharge readiness, attendance, response to treatment and the
     * ration due at the next review.
     *
     * <p>Stateless like the growth engine — pct-service owns the episode and its reviews and supplies
     * them. Three answers this endpoint refuses to give: a discharge on one good measurement, an
     * oedema-free certification from an assessment nobody recorded, and a reassuring status for a
     * child with no review on file at all.</p>
     */
    @PostMapping("/paediatric/imam/progress")
    public ResponseEntity<Map<String, Object>> imamProgress(@RequestBody Map<String, Object> body) {
        Object programme = body.get("programme");
        if (programme == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "programme_required",
                    "message", "Discharge criteria, review cadence and the defaulter definition differ by "
                               + "programme; without one there is nothing to evaluate against."));
        }
        Object admittedRaw = body.get("admitted_on") != null ? body.get("admitted_on") : body.get("admittedOn");
        java.time.LocalDate admittedOn = admittedRaw == null ? null
                : java.time.LocalDate.parse(String.valueOf(admittedRaw).substring(0, 10));
        Object asOfRaw = body.get("as_of") != null ? body.get("as_of") : body.get("asOf");
        java.time.LocalDate asOf = asOfRaw == null ? java.time.LocalDate.now()
                : java.time.LocalDate.parse(String.valueOf(asOfRaw).substring(0, 10));
        Object oedemaRaw = body.get("admission_oedema") != null
                ? body.get("admission_oedema") : body.get("admissionOedema");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> visits = body.get("visits") instanceof List<?> l
                ? (List<Map<String, Object>>) (List<?>) l : List.of();

        var assessment = imamProgrammeService.progress(String.valueOf(programme), admittedOn,
                oedemaRaw == null ? null : String.valueOf(oedemaRaw), visits, asOf);
        return ResponseEntity.ok(Map.of("data",
                zw.gov.mohcc.impilo.clinical.imam.ImamProgrammeService.toMap(assessment)));
    }

    @PostMapping("/interpretation/evaluate")
    public ResponseEntity<Map<String, Object>> interpretationEvaluate(
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> context = body.get("context") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observations = body.get("observations") instanceof List<?> l
                ? (List<Map<String, Object>>) (List<?>) l
                : List.of();
        String patientId = body.get("patient_id") != null ? body.get("patient_id").toString() : null;
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        Map<String, Object> data = interpretationEvaluationService.evaluate(
                actorId, patientId, encounterId, context, observations);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @PostMapping("/cds/summary")
    public ResponseEntity<Map<String, Object>> cdsSummary(
            @RequestHeader(value = "x-tenant-id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> patientContext = body.get("patient_context") instanceof Map<?, ?> m
                ? new java.util.LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = body.get("alerts") instanceof List<?> l
                ? (List<Map<String, Object>>) (List<?>) l
                : List.of();
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        Map<String, Object> data = cdsInsightService.summarise(tenantId, actorId, patientContext, alerts, encounterId);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> pathways() {
        return ResponseEntity.ok(Map.of("data", pathwaySessionService.listPathways()));
    }

    @GetMapping("/pathways/{id}")
    public ResponseEntity<Map<String, Object>> pathwayDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("data", pathwaySessionService.getPathwayDetail(id)));
    }

    @PostMapping("/pathways/sessions")
    public ResponseEntity<Map<String, Object>> startPathway(
            @RequestHeader(value = "x-tenant-id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {
        UUID pathwayId = UUID.fromString(body.get("pathway_id").toString());
        String patientId = body.get("patient_id") != null ? body.get("patient_id").toString() : null;
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        PathwaySessionEntity s = pathwaySessionService.start(tenantId, actorId, pathwayId, patientId, encounterId);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("session_id", s.getId().toString());
        d.put("pathway_id", s.getPathwayId().toString());
        d.put("current_step_order", s.getCurrentStepOrder());
        d.put("status", s.getStatus());
        d.putAll(pathwaySessionService.currentStepSnapshot(s));
        return ResponseEntity.ok(Map.of("data", d));
    }

    @PostMapping("/pathways/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advancePathway(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = body.get("answers") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        return ResponseEntity.ok(Map.of("data", pathwaySessionService.advance(sessionId, answers)));
    }

    @PostMapping("/nudges/evaluate")
    public ResponseEntity<Map<String, Object>> nudges(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", nudgeEvaluationService.evaluate(body)));
    }

    @PostMapping("/audit/overrides")
    public ResponseEntity<Map<String, Object>> recordOverride(
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {
        UUID traceId = UUID.fromString(body.get("recommendation_trace_id").toString());
        String reason = body.get("override_reason").toString();
        OverrideRecordEntity o = new OverrideRecordEntity();
        o.setRecommendationTraceId(traceId);
        o.setOverrideReason(reason);
        o.setOverriddenBy(actorId);
        OverrideRecordEntity saved = overrideRecordRepository.save(o);
        clinicalOutboxWriter.enqueue(
                "GUIDANCE_OVERRIDE_RECORDED",
                "default",
                "OverrideRecord",
                saved.getId().toString(),
                Map.of(
                        "override_id", saved.getId().toString(),
                        "recommendation_trace_id", traceId.toString(),
                        "overridden_by", actorId));
        return ResponseEntity.ok(Map.of("data", Map.of("status", "recorded", "override_id", saved.getId().toString())));
    }
}
