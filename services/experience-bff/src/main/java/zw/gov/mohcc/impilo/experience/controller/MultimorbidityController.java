package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The multimorbidity view (brief.md §9), composed for one patient.
 *
 * <p>Composition is the whole job here. §9's detections are about relationships between sources —
 * a diet conflict between two conditions, a duplicate between two medicines, a repeat between two
 * orders — so somebody has to hold all the sources at once, and that is this layer. The engine in
 * CKP does the reasoning; nothing clinical is decided here.</p>
 *
 * <p><strong>A source that could not be read is omitted, never emptied.</strong> The engine treats a
 * missing key as "not obtained" and an empty array as "there are none", and answers UNDETERMINED for
 * the first and NOT_DETECTED for the second. Defaulting a failed read to {@code []} — the reflex
 * that makes most composition code tidy — would convert every outage into a clean bill of health on
 * the one screen a clinician opens to find what the other screens hid.</p>
 */
@RestController
@RequestMapping("/internal/v1/medicine/multimorbidity")
public class MultimorbidityController {

    private static final Logger log = LoggerFactory.getLogger(MultimorbidityController.class);

    private final ClinicalKnowledgePlatformClient ckp;
    private final PctServiceClient pct;
    private final OrosServiceClient oros;
    private final BookingServiceClient booking;
    private final ObjectMapper objectMapper;

    public MultimorbidityController(ClinicalKnowledgePlatformClient ckp, PctServiceClient pct,
                                    OrosServiceClient oros, BookingServiceClient booking,
                                    ObjectMapper objectMapper) {
        this.ckp = ckp;
        this.pct = pct;
        this.oros = oros;
        this.booking = booking;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> assess(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        ObjectNode body = objectMapper.createObjectNode();

        // The subject the detected issues belong to. Without it CKP evaluates and files nothing to
        // the shared health record, because an issue with no subject cannot be attached to a
        // patient — so the §9 findings would exist on this screen and nowhere else. This layer holds
        // the CPID; sending it is what makes the DetectedIssue producer reachable in production.
        body.put("subjectCpid", subjectCpid);

        addConditions(body, subjectCpid);
        addMedicines(body, subjectCpid);
        addAppointments(body, subjectCpid);
        addInvestigations(body, subjectCpid);
        addFunctionalLimitations(body, subjectCpid);
        // patientPriorities and careTeam stay omitted, not because this layer failed to read them,
        // but because nothing in the estate captures either concept yet. A patient's advance
        // directive is a legal end-of-life instruction, not "what matters most to them" day to day,
        // and an MDT board's participant list is a per-case ad hoc roster, not a standing care-team
        // assignment — using either as a stand-in would answer §9's questions with the wrong data
        // rather than admit no data exists. Wiring these needs a capability built first, in whichever
        // service should own it (not this pack, per the architecture guardrails against inventing a
        // second home for a concept nobody has built a first home for yet).

        try {
            JsonNode data = ckp.multimorbidityAssess(body);
            return ResponseEntity.ok(Map.of(
                    "data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("CKP multimorbidity assessment failed for subject={}: {}", subjectCpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "multimorbidity_unavailable",
                    "message", "The multimorbidity view could not be assembled. Nothing was checked — "
                               + "do not read this as an absence of conflicts.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private void addConditions(ObjectNode body, String subjectCpid) {
        try {
            JsonNode problems = pct.listProblems(subjectCpid);
            if (problems == null || !problems.isArray()) {
                log.warn("Problem list unreadable for subject={} — omitted so the engine reports it "
                         + "unanswered rather than reporting no conditions", subjectCpid);
                return;
            }
            ArrayNode conditions = body.putArray("conditions");
            for (JsonNode p : problems) {
                if (isResolved(p.path("clinical_status").asText(p.path("clinicalStatus").asText("")))) {
                    continue;
                }
                ObjectNode c = conditions.addObject();
                c.put("code", p.path("code").asText(""));
                c.put("display", p.path("display").asText(""));
                // Chronicity is not stored on a problem — pct V100 deliberately removed CHRONIC as a
                // category ("a chronicity attribute rather than a kind") and nothing replaced it. We
                // therefore cannot assert it, and asserting it falsely would inflate the burden score.
                c.put("longTerm", false);
            }
        } catch (Exception e) {
            log.warn("Problem list could not be read for subject={}: {} — omitted, not emptied",
                    subjectCpid, e.getMessage());
            body.remove("conditions");
        }
    }

    private void addMedicines(ObjectNode body, String subjectCpid) {
        List<String> medicines = oros.currentMedicationCodes(subjectCpid);
        if (medicines == null) {
            log.warn("Medicine list unavailable for subject={} — omitted so duplication and unsafe "
                     + "combinations report unanswered rather than clear", subjectCpid);
            return;
        }
        ArrayNode array = body.putArray("medicationCodes");
        medicines.forEach(array::add);
    }

    /**
     * The citizen's appointment history from booking-service, keyed by CPID.
     *
     * <p>A cancelled appointment did not happen and must not count toward visit burden; it is
     * dropped rather than sent, the same way a resolved condition is dropped from
     * {@link #addConditions}. An appointment with no parseable scheduled time is dropped for the
     * same reason the CKP controller itself drops undated appointments — it cannot be placed in the
     * burden window, and a burden count is honest only about what it could place.</p>
     */
    private void addAppointments(ObjectNode body, String subjectCpid) {
        try {
            JsonNode appointments = booking.listCitizenAppointments(subjectCpid, null, 0, 50);
            if (appointments == null || !appointments.isArray()) {
                log.warn("Appointment book unreadable for subject={} — omitted so visit burden reports "
                         + "unanswered rather than clear", subjectCpid);
                return;
            }
            ArrayNode out = body.putArray("appointments");
            for (JsonNode a : appointments) {
                if ("CANCELLED".equalsIgnoreCase(a.path("status").asText(""))) {
                    continue;
                }
                LocalDate date = toLocalDate(firstText(a, "scheduled_at", "scheduledAt"));
                if (date == null) {
                    continue;
                }
                ObjectNode entry = out.addObject();
                entry.put("date", date.toString());
                String clinic = firstText(a, "facility_name", "facilityName");
                if (clinic != null) {
                    entry.put("clinic", clinic);
                }
                String reason = a.path("reason").asText(null);
                if (reason != null && !reason.isBlank()) {
                    entry.put("reason", reason);
                }
            }
        } catch (Exception e) {
            log.warn("Appointment book could not be read for subject={}: {} — omitted, not emptied",
                    subjectCpid, e.getMessage());
            body.remove("appointments");
        }
    }

    /**
     * The patient's lab and imaging order history from OROS, keyed by CPID.
     *
     * <p>{@code /v1/orders/patient/{cpid}} returns every order type; PHARMACY and PROCEDURE orders
     * are filtered out here because §9's repeated-investigations check is about tests, not medicines
     * or procedures — sending them would let a course of dressing changes read as a repeated blood
     * test. An order with no parseable placed time is dropped for the same reason an undated
     * appointment is: it cannot be placed against another investigation to detect a repeat.</p>
     */
    private void addInvestigations(ObjectNode body, String subjectCpid) {
        try {
            JsonNode orders = oros.getPatientOrders(subjectCpid);
            if (orders == null || !orders.isArray()) {
                log.warn("Order history unreadable for subject={} — omitted so repeated-investigation "
                         + "checks report unanswered rather than clear", subjectCpid);
                return;
            }
            ArrayNode out = body.putArray("investigations");
            for (JsonNode o : orders) {
                String type = firstText(o, "order_type", "orderType");
                if (!"LAB".equalsIgnoreCase(type) && !"IMAGING".equalsIgnoreCase(type)) {
                    continue;
                }
                LocalDate date = toLocalDate(firstText(o, "placed_at", "placedAt"));
                if (date == null) {
                    continue;
                }
                ObjectNode entry = out.addObject();
                String code = firstText(o, "test_code", "testCode", "zibo_order_code", "ziboOrderCode");
                if (code != null) {
                    entry.put("code", code);
                }
                entry.put("date", date.toString());
                String orderedBy = firstText(o, "placed_by", "placedBy");
                if (orderedBy != null) {
                    entry.put("orderedBy", orderedBy);
                }
            }
        } catch (Exception e) {
            log.warn("Order history could not be read for subject={}: {} — omitted, not emptied",
                    subjectCpid, e.getMessage());
            body.remove("investigations");
        }
    }

    /**
     * The patient's recorded functional assessments (ECOG/Barthel/Lawton/Katz etc.) from pct's
     * structured history, rendered as the descriptive strings §9's functional-impact panel displays
     * as-is.
     *
     * <p>A scoreless assessment is not dropped: {@code pct_functional_assessments} carries a score
     * OR a reason it could not be scored, never neither (see {@code FunctionalAssessmentEntity}), and
     * "could not be assessed" is itself a functional fact worth showing, not a gap to paper over.</p>
     */
    private void addFunctionalLimitations(ObjectNode body, String subjectCpid) {
        try {
            JsonNode assessments = pct.getFunctionalAssessments(subjectCpid);
            if (assessments == null || !assessments.isArray()) {
                log.warn("Functional assessments unreadable for subject={} — omitted so the panel "
                         + "reports unknown rather than no limitations", subjectCpid);
                return;
            }
            ArrayNode out = body.putArray("functionalLimitations");
            for (JsonNode a : assessments) {
                out.add(describeFunctionalAssessment(a));
            }
        } catch (Exception e) {
            log.warn("Functional assessments could not be read for subject={}: {} — omitted, not emptied",
                    subjectCpid, e.getMessage());
            body.remove("functionalLimitations");
        }
    }

    private static String describeFunctionalAssessment(JsonNode a) {
        String type = firstText(a, "assessment_type", "assessmentType");
        if (type == null) {
            type = "Functional assessment";
        }
        String absentReason = firstText(a, "score_absent_reason", "scoreAbsentReason");
        if (absentReason != null && !absentReason.isBlank()) {
            return type + ": not assessable (" + absentReason + ")";
        }
        StringBuilder sb = new StringBuilder(type);
        if (a.hasNonNull("totalScore") || a.hasNonNull("total_score")) {
            sb.append(' ').append(firstNode(a, "total_score", "totalScore").asInt());
            JsonNode max = firstNode(a, "max_score", "maxScore");
            if (max != null && !max.isNull()) {
                sb.append('/').append(max.asInt());
            }
        }
        String interpretation = firstText(a, "interpretation");
        if (interpretation != null && !interpretation.isBlank()) {
            sb.append(" (").append(interpretation).append(')');
        }
        return sb.toString();
    }

    /** First non-blank text value found under any of the given field names, or null. */
    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String v = node.path(field).asText(null);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    /**
     * Parses an ISO instant/offset/plain date string down to its calendar date. Accepts whichever
     * shape the upstream service serialized (an {@code Instant}, an {@code OffsetDateTime}, or a
     * bare date) rather than assuming one, because CKP's own parser only accepts a bare date and a
     * shape mismatch here would silently drop every appointment and investigation.
     */
    private static LocalDate toLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return java.time.Instant.parse(raw).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isResolved(String clinicalStatus) {
        if (clinicalStatus == null || clinicalStatus.isBlank()) {
            return false;
        }
        return switch (clinicalStatus.toUpperCase(java.util.Locale.ROOT)) {
            case "RESOLVED", "INACTIVE" -> true;
            default -> false;
        };
    }
}
