package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The clinician-facing maternal near-miss surface.
 *
 * <p>Its reason to exist is {@link #classifyFromForm}: form 21 has been seeded in forms-service and
 * the classification engine has been in CKP, with nothing joining them. A midwife could fill the form
 * and no near-miss was ever identified from it. This translates form-21 answers into the WHO
 * observation set and returns the classification, which is what makes the criteria reachable by the
 * person at the bedside instead of only by a test.
 *
 * <h2>Not on the confidential lane</h2>
 * <p>Near-miss identification carries no confidentiality stamp — it is ordinary clinical
 * classification, and the near-miss-to-death ratio has to remain computable. Only the MPDSR review is
 * a governed confidential instrument, and it is not this.
 *
 * <h2>The whole risk lives in three ways of saying "no"</h2>
 * <p>A form field can be answered ABSENT, left blank, or answered with something this code does not
 * recognise. Only the first is a clinical negative. Collapsing the other two into false is how a woman
 * who nearly died gets recorded as a normal birth — and because it also removes her from the numerator
 * of the near-miss-to-death ratio, the register improves at the same moment the learning is lost. So
 * blank is omitted and unrecognised is refused.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/near-miss")
public class MaternalNearMissController {

    private static final Logger log = LoggerFactory.getLogger(MaternalNearMissController.class);

    /** Form-21 linkId prefix. The engine's fact keys carry it too, so the mapping is one-to-one. */
    private static final String LINK_PREFIX = "nearMiss.";

    /**
     * The boolean criteria, by their bare field name. Held explicitly rather than inferred from the
     * value, because inferring the type from what arrived would make a stray "PRESENT" in a numeric
     * field silently become a boolean true.
     */
    private static final Set<String> BOOLEAN_FIELDS = Set.of(
            "shock", "cardiacArrest", "continuousVasoactiveDrugs", "cardiopulmonaryResuscitation",
            "acuteCyanosis", "gasping", "oxygenSaturationBelow90ForOverAnHour",
            "oliguriaNonResponsiveToFluids", "dialysisForAcuteRenalFailure",
            "clottingFailure", "jaundiceWithPreEclampsia",
            "prolongedUnconsciousness", "stroke", "uncontrollableFits", "totalParalysis",
            "hysterectomyForInfectionOrHaemorrhage");

    /** The measured criteria. Decimal on the form; the engine compares them against the table. */
    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "lactateMmolL", "phArterial", "respiratoryRate", "intubationNotForAnaesthesiaMinutes",
            "creatinineUmolL", "plateletsPerUl", "unitsTransfused", "bilirubinUmolL");

    /**
     * Codes that mean "this was not established". Form 21 offers only PRESENT and ABSENT, but a client
     * may carry an explicit unknown, and an explicit unknown must behave exactly like a blank rather
     * than being refused as a bad code.
     */
    private static final Set<String> UNKNOWN_CODES = Set.of(
            "UNKNOWN", "NOT_ASSESSED", "NOT_RECORDED", "NOT_DONE", "INDETERMINATE");

    private final ClinicalKnowledgePlatformClient ckp;

    public MaternalNearMissController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    /** Form-21 answers keyed by linkId, as the form renderer collected them. */
    public record FormClassifyRequest(String formKey, Map<String, Object> answers) {
    }

    /**
     * Classifies a woman from a completed form 21.
     *
     * <p>Answers are translated, not validated into existence: a field the clinician did not fill is
     * left out of the observation set entirely so its criterion stays unresolved and the outcome is
     * INDETERMINATE. That is the honest answer — the form is deliberately all-optional because organ
     * dysfunction is assessed with whatever was available, and demanding every field would push staff
     * to answer "no" to get past the screen.
     */
    @PostMapping("/classify-form")
    public ResponseEntity<Map<String, Object>> classifyFromForm(
            @RequestBody(required = false) FormClassifyRequest request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Map<String, Object> observations = new LinkedHashMap<>();
        List<String> unrecognised = new ArrayList<>();
        List<String> omitted = new ArrayList<>();

        Map<String, Object> answers = request == null || request.answers() == null
                ? Map.of() : request.answers();

        for (Map.Entry<String, Object> e : answers.entrySet()) {
            String field = e.getKey() == null ? "" : e.getKey().trim();
            if (field.startsWith(LINK_PREFIX)) {
                field = field.substring(LINK_PREFIX.length());
            }
            Object raw = e.getValue();

            if (BOOLEAN_FIELDS.contains(field)) {
                Boolean coded = codedBoolean(raw);
                if (coded == null) {
                    if (isBlankOrUnknown(raw)) {
                        omitted.add(field);
                    } else {
                        unrecognised.add(LINK_PREFIX + field + "=" + raw);
                    }
                } else {
                    observations.put(field, coded);
                }
            } else if (NUMERIC_FIELDS.contains(field)) {
                if (isBlankOrUnknown(raw)) {
                    omitted.add(field);
                    continue;
                }
                Number n = number(raw);
                if (n == null) {
                    unrecognised.add(LINK_PREFIX + field + "=" + raw);
                } else {
                    observations.put(field, n);
                }
            }
            // An unknown linkId is ignored: form 21 may gain a field before this map does, and
            // refusing the whole submission would block a classification the known fields can
            // already support. It cannot cause a false negative, because an unmapped answer never
            // becomes a fact — it leaves its criterion unresolved, which reads as INDETERMINATE.
        }

        if (!unrecognised.isEmpty()) {
            // Refused rather than dropped. A dropped bad value leaves the criterion unresolved, which
            // is safe but silent, and the clinician would see INDETERMINATE with no idea that the
            // answer she gave never arrived.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "unrecognised_form_answer");
            body.put("message", "These answers could not be read, and were not treated as negatives. "
                    + "An unreadable answer silently becoming 'absent' is how a near-miss is recorded "
                    + "as a normal birth. Correct them and resubmit.");
            body.put("unrecognised_answers", unrecognised);
            body.put("accepted_codes", List.of("PRESENT", "ABSENT"));
            body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.unprocessableEntity().body(body);
        }

        return proxy(() -> ckp.nearMissClassify(observations), requestId, correlationId,
                Map.of("form_key", request == null || request.formKey() == null
                                ? "impilo.maternal.nearmiss.assessment.v1" : request.formKey(),
                        "criteria_recorded", observations.size(),
                        // Named, not just counted: the clinician's next action is to fill these in,
                        // and "3 unrecorded" does not say which three.
                        "criteria_left_blank", omitted));
    }

    /**
     * Classifies from a typed observation set, for a caller that is not a form renderer.
     *
     * <p>Forwarded as received. The BFF adds no defaults, because the only default available for an
     * absent organ-dysfunction observation is a false one.
     */
    @PostMapping("/classify")
    public ResponseEntity<Map<String, Object>> classify(
            @RequestBody(required = false) Map<String, Object> observations,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Map<String, Object> body = observations == null ? Map.of() : observations;
        return proxy(() -> ckp.nearMissClassify(body), requestId, correlationId,
                Map.of("criteria_recorded", body.size()));
    }

    /**
     * The near-miss-to-death ratio and mortality index for a reporting period.
     *
     * <p>CKP refuses an unclassifiable outcome with a 422 naming the offending case, and that refusal
     * is forwarded intact. Collapsed into a 502 it would read as "the service is down" when the real
     * answer is "case 7 has an outcome nobody can count".
     */
    @PostMapping("/indicators")
    public ResponseEntity<Map<String, Object>> indicators(
            @RequestBody Map<String, Object> request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        return proxy(() -> ckp.nearMissIndicators(request == null ? Map.of() : request),
                requestId, correlationId, Map.of());
    }

    /** PRESENT/ABSENT, or a real boolean from a client that already typed it. Null when unreadable. */
    private static Boolean codedBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim().toUpperCase();
        return switch (s) {
            case "PRESENT", "TRUE", "YES" -> Boolean.TRUE;
            case "ABSENT", "FALSE", "NO" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static boolean isBlankOrUnknown(Object raw) {
        if (raw == null) {
            return true;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() || UNKNOWN_CODES.contains(s.toUpperCase());
    }

    private static Number number(Object raw) {
        if (raw instanceof Number n) {
            return n;
        }
        try {
            return Double.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> proxy(Supplier<JsonNode> call,
                                                      String requestId,
                                                      String correlationId,
                                                      Map<String, Object> extraMeta) {
        Map<String, Object> meta = new LinkedHashMap<>(extraMeta);
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        try {
            JsonNode data = call.get();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", data == null ? Map.of() : data);
            body.put("meta", meta);
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException e) {
            // CKP's 422 carries which case or which answer was refused. Flattening it to a 502 would
            // send a clinician looking for an outage instead of at the field she needs to fix.
            log.warn("CKP near-miss returned {}: {}", e.getStatusCode(), e.getStatusText());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("upstream_status", e.getStatusCode().value());
            body.put("upstream_body", e.getResponseBodyAsString());
            body.put("meta", meta);
            return ResponseEntity.status(e.getStatusCode()).body(body);
        } catch (Exception e) {
            log.error("CKP near-miss call failed: {}", e.getMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "near_miss_unavailable");
            // An empty 200 would render as NEAR_MISS_NOT_MET in any client that reads only the code,
            // turning an outage into a clinical all-clear.
            body.put("message", "The near-miss criteria could not be evaluated. This is not a finding "
                    + "of 'no near-miss'. Classify clinically and resubmit when the service returns.");
            body.put("meta", meta);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
    }
}
