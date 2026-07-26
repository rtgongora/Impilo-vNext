package zw.gov.mohcc.impilo.clinical.imam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the governed IMAM programme pack and runs the engine over it.
 *
 * <p>The service is stateless with respect to the child: pct-service owns the episode and its
 * reviews. That boundary is deliberate and matches the growth and immunisation engines — an episode
 * held in two places would eventually disagree with itself about whether a child was discharged.</p>
 */
@Service
public class ImamProgrammeService {

    private static final Logger log = LoggerFactory.getLogger(ImamProgrammeService.class);

    static final String CONTENT_PATH = "clinical/imam-programme.json";

    private final ObjectMapper objectMapper;
    private ImamProgrammeContent cached;

    public ImamProgrammeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized ImamProgrammeContent content() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    public ImamProgrammeEngine.Eligibility eligibility(Map<String, Object> facts) {
        return ImamProgrammeEngine.eligibility(content(), toFacts(facts == null ? Map.of() : facts));
    }

    public ImamProgrammeEngine.Progress progress(String programme, LocalDate admittedOn, String admissionOedema,
                                                 List<Map<String, Object>> visits, LocalDate asOf) {
        List<ImamProgrammeEngine.Visit> parsed = new ArrayList<>();
        for (Map<String, Object> v : visits == null ? List.<Map<String, Object>>of() : visits) {
            parsed.add(toVisit(v));
        }
        return ImamProgrammeEngine.progress(content(), programme, admittedOn, admissionOedema, parsed, asOf);
    }

    static ImamProgrammeEngine.EligibilityFacts toFacts(Map<String, Object> f) {
        return new ImamProgrammeEngine.EligibilityFacts(
                integer(f, "ageDays", "age_days"),
                decimal(f, "muacCm", "muac_cm"),
                text(f, "bilateralPittingOedema", "bilateral_pitting_oedema", "oedema"),
                text(f, "appetiteTest", "appetite_test"),
                decimal(f, "weightForHeightZ", "weight_for_height_z"),
                bool(f, "medicalComplication", "medical_complication"),
                bool(f, "dangerSignsPresent", "danger_signs_present"),
                text(f, "classification", "imnci_classification", "source_classification"));
    }

    static ImamProgrammeEngine.Visit toVisit(Map<String, Object> v) {
        Boolean attended = bool(v, "attended");
        return new ImamProgrammeEngine.Visit(
                integer(v, "visitNumber", "visit_number"),
                date(v, "visitDate", "visit_date"),
                attended == null || attended,
                decimal(v, "muacCm", "muac_cm"),
                decimal(v, "weightKg", "weight_kg"),
                text(v, "oedema", "bilateralPittingOedema", "bilateral_pitting_oedema"),
                text(v, "appetiteTest", "appetite_test"),
                bool(v, "dangerSignsPresent", "danger_signs_present"));
    }

    // ── content loading ─────────────────────────────────────────────────────────────────────────

    private ImamProgrammeContent load() {
        try (InputStream stream = ImamProgrammeService.class.getClassLoader().getResourceAsStream(CONTENT_PATH)) {
            if (stream == null) {
                log.error("IMAM programme content not found on the classpath: {}", CONTENT_PATH);
                return empty();
            }
            JsonNode root = objectMapper.readTree(stream);

            List<ImamProgrammeContent.Programme> programmes = new ArrayList<>();
            int rejected = 0;
            for (JsonNode p : root.path("programmes")) {
                String code = p.path("code").asText(null);
                JsonNode dcNode = p.path("dischargeCriteria");
                if (code == null || dcNode.isMissingNode()) {
                    // A programme a child can be admitted to but never discharged from is a trap:
                    // the episode would stay open for ever and the caseload would never reconcile.
                    log.error("IMAM programme rejected: missing code or discharge criteria");
                    rejected++;
                    continue;
                }
                programmes.add(new ImamProgrammeContent.Programme(
                        code,
                        p.path("name").asText(code),
                        p.path("treats").asText(null),
                        strings(p.path("admitsClassifications")),
                        admissionCriteria(p.path("admissionCriteria")),
                        p.path("reviewIntervalDays").asInt(7),
                        p.path("attendanceGraceDays").asInt(0),
                        p.path("traceAfterMissedVisits").asInt(1),
                        p.path("defaulterMissedVisits").asInt(2),
                        p.path("nonResponderAfterDays").asInt(Integer.MAX_VALUE),
                        dec(p, "targetWeightGainGramsPerKgPerDay"),
                        therapeuticFood(p.path("therapeuticFood")),
                        new ImamProgrammeContent.DischargeCriteria(
                                dcNode.path("narrative").asText(null),
                                dec(dcNode, "muacAtLeastCm"),
                                dcNode.path("sustainedConsecutiveVisits").asInt(2),
                                dcNode.path("oedemaFreeMinimumDays").asInt(0),
                                dcNode.path("minimumStayDays").asInt(0),
                                dcNode.path("requiresNoAcuteMedicalProblem").asBoolean(false),
                                dcNode.path("requiresAppetiteReturned").asBoolean(false),
                                dcNode.path("requiresOedemaResolving").asBoolean(false),
                                dcNode.hasNonNull("dischargeDestination")
                                        ? dcNode.get("dischargeDestination").asText() : null,
                                dcNode.path("dischargeOutcomeIsCure").asBoolean(true))));
            }

            List<ImamProgrammeContent.Escalation> escalations = new ArrayList<>();
            for (JsonNode e : root.path("escalations")) {
                escalations.add(new ImamProgrammeContent.Escalation(
                        e.path("code").asText(null),
                        e.path("fromProgramme").asText(null),
                        e.path("when").asText(null),
                        e.path("toProgramme").asText(null),
                        e.path("action").asText(null)));
            }

            List<ImamProgrammeContent.Outcome> outcomes = new ArrayList<>();
            for (JsonNode o : root.path("outcomes")) {
                outcomes.add(new ImamProgrammeContent.Outcome(
                        o.path("code").asText(null),
                        o.path("name").asText(null),
                        o.path("requiresDischargeCriteria").asBoolean(false),
                        o.path("description").asText(null)));
            }

            List<ImamProgrammeContent.TestCase> cases = new ArrayList<>();
            for (JsonNode c : root.path("testCases")) {
                cases.add(new ImamProgrammeContent.TestCase(
                        c.path("kind").asText("ELIGIBILITY"),
                        c.path("name").asText("unnamed"),
                        c.hasNonNull("facts") ? map(c.get("facts")) : Map.of(),
                        c.hasNonNull("expectEligible") ? c.get("expectEligible").asBoolean() : null,
                        c.hasNonNull("expectAssessable") ? c.get("expectAssessable").asBoolean() : null,
                        c.hasNonNull("expectProgramme") ? c.get("expectProgramme").asText() : null,
                        c.hasNonNull("expectAdmissionCriterion") ? c.get("expectAdmissionCriterion").asText() : null,
                        c.hasNonNull("programme") ? c.get("programme").asText() : null,
                        c.hasNonNull("admittedOn") ? c.get("admittedOn").asText() : null,
                        c.hasNonNull("admissionOedema") ? c.get("admissionOedema").asText() : null,
                        c.hasNonNull("asOf") ? c.get("asOf").asText() : null,
                        maps(c.path("visits")),
                        c.hasNonNull("expectDischargeEligible") ? c.get("expectDischargeEligible").asBoolean() : null,
                        strings(c.path("expectUnmetCriteria")),
                        c.hasNonNull("expectAttendanceStatus") ? c.get("expectAttendanceStatus").asText() : null,
                        c.hasNonNull("expectResponseStatus") ? c.get("expectResponseStatus").asText() : null,
                        c.hasNonNull("expectEscalation") ? c.get("expectEscalation").asText() : null,
                        c.hasNonNull("expectNoReviewRecorded")
                                ? c.get("expectNoReviewRecorded").asBoolean() : null));
            }

            log.info("Loaded IMAM programme pack {} version={} approval={} programmes={} rejected={} fixtures={}",
                    root.path("packId").asText(CONTENT_PATH), root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"), programmes.size(), rejected, cases.size());

            return new ImamProgrammeContent(
                    root.path("packId").asText(CONTENT_PATH),
                    root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"),
                    root.path("adaptationAuthority").asText(null),
                    root.path("provenance").asText(null),
                    List.copyOf(programmes), List.copyOf(escalations), List.copyOf(outcomes), List.copyOf(cases));

        } catch (IOException e) {
            log.error("Failed to read IMAM programme content: {}", e.getMessage(), e);
            return empty();
        }
    }

    private static List<ImamProgrammeContent.AdmissionCriterion> admissionCriteria(JsonNode node) {
        List<ImamProgrammeContent.AdmissionCriterion> out = new ArrayList<>();
        for (JsonNode c : node) {
            out.add(new ImamProgrammeContent.AdmissionCriterion(
                    c.path("code").asText(null),
                    c.path("description").asText(null),
                    c.path("combinator").asText("ALL"),
                    dec(c, "muacBelowCm"),
                    dec(c, "muacAtLeastCm"),
                    c.path("oedemaPresent").asBoolean(false),
                    dec(c, "weightForHeightZBelow"),
                    dec(c, "weightForHeightZAtLeast"),
                    c.path("requiresComplication").asBoolean(false),
                    c.hasNonNull("minAgeDays") ? c.get("minAgeDays").asInt() : null,
                    c.hasNonNull("maxAgeDays") ? c.get("maxAgeDays").asInt() : null,
                    c.path("optionalCriterion").asBoolean(false)));
        }
        return List.copyOf(out);
    }

    private static ImamProgrammeContent.TherapeuticFood therapeuticFood(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        List<ImamProgrammeContent.RationBand> bands = new ArrayList<>();
        for (JsonNode b : node.path("rationBands")) {
            bands.add(new ImamProgrammeContent.RationBand(
                    dec(b, "minWeightKg"), dec(b, "maxWeightKg"),
                    dec(b, "sachetsPerDay"), dec(b, "sachetsPerWeek")));
        }
        return new ImamProgrammeContent.TherapeuticFood(
                node.path("productCode").asText(null),
                node.path("productName").asText(null),
                node.path("rationBasis").asText(null),
                node.path("note").asText(null),
                List.copyOf(bands));
    }

    private static ImamProgrammeContent empty() {
        return new ImamProgrammeContent(CONTENT_PATH, "unavailable", "ENGINEERING_SEED", null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    // ── serialisation shared by the endpoints and the fixture gate ───────────────────────────────

    public static Map<String, Object> toMap(ImamProgrammeEngine.Eligibility e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessable", e.assessable());
        m.put("not_assessable_reason", e.notAssessableReason());
        m.put("eligible", e.eligible());
        m.put("programme", e.programme());
        m.put("programme_name", e.programmeName());
        m.put("admission_criterion", e.admissionCriterion());
        m.put("admission_criterion_description", e.admissionCriterionDescription());
        m.put("criteria_met", e.criteriaMet());
        m.put("missing_inputs", e.missingInputs());
        m.put("review_interval_days", e.reviewIntervalDays());
        m.put("therapeutic_food", e.therapeuticFood());
        m.put("rationale", e.rationale());
        m.put("content_version", e.contentVersion());
        m.put("approval_status", e.approvalStatus());
        m.put("note", e.note());
        return m;
    }

    public static Map<String, Object> toMap(ImamProgrammeEngine.Progress p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programme", p.programme());
        m.put("programme_name", p.programmeName());
        m.put("assessable", p.assessable());
        m.put("not_assessable_reason", p.notAssessableReason());
        m.put("reviews_recorded", p.reviewsRecorded());
        m.put("no_review_recorded", p.noReviewRecorded());
        m.put("days_in_programme", p.daysInProgramme());
        m.put("discharge_eligible", p.dischargeEligible());
        List<Map<String, Object>> criteria = new ArrayList<>();
        for (ImamProgrammeEngine.Criterion c : p.dischargeCriteria()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("code", c.code());
            cm.put("name", c.name());
            cm.put("met", c.met());
            cm.put("detail", c.detail());
            criteria.add(cm);
        }
        m.put("discharge_criteria", criteria);
        m.put("unmet_criteria", p.unmetCriteria());
        m.put("discharge_outcome", p.dischargeOutcome());
        m.put("discharge_destination", p.dischargeDestination());
        m.put("attendance_status", p.attendanceStatus());
        m.put("consecutive_missed_visits", p.consecutiveMissedVisits());
        m.put("days_since_last_contact", p.daysSinceLastContact());
        m.put("next_review_due", p.nextReviewDue() == null ? null : p.nextReviewDue().toString());
        m.put("days_overdue", p.daysOverdue());
        m.put("tracing_required", p.tracingRequired());
        m.put("response_status", p.responseStatus());
        m.put("weight_gain_g_per_kg_per_day", p.weightGainGramsPerKgPerDay());
        m.put("escalation", p.escalation());
        m.put("escalation_action", p.escalationAction());
        m.put("recommended_sachets_per_week", p.recommendedSachetsPerWeek());
        m.put("therapeutic_food", p.therapeuticFood());
        m.put("actions", p.actions());
        m.put("content_version", p.contentVersion());
        m.put("approval_status", p.approvalStatus());
        m.put("note", p.note());
        return m;
    }

    // ── lenient coercion; the BFF, PCT and offline packs each send a different dialect ────────────

    private static BigDecimal dec(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).decimalValue() : null;
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asText()));
        return List.copyOf(out);
    }

    private Map<String, Object> map(JsonNode node) {
        return objectMapper.convertValue(node, objectMapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, Object.class));
    }

    private List<Map<String, Object>> maps(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        node.forEach(n -> out.add(map(n)));
        return List.copyOf(out);
    }

    static BigDecimal decimal(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v instanceof Number num) {
                return new BigDecimal(num.toString());
            }
            if (v != null && !String.valueOf(v).isBlank()) {
                try {
                    return new BigDecimal(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                    // try the next alias
                }
            }
        }
        return null;
    }

    static Integer integer(Map<String, Object> row, String... keys) {
        BigDecimal d = decimal(row, keys);
        return d == null ? null : d.intValue();
    }

    static String text(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    static Boolean bool(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v instanceof Boolean b) {
                return b;
            }
            if (v != null && !String.valueOf(v).isBlank()) {
                String s = String.valueOf(v).trim();
                if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")) {
                    return Boolean.TRUE;
                }
                if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no")) {
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    static LocalDate date(Map<String, Object> row, String... keys) {
        String t = text(row, keys);
        if (t == null || t.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(t.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
