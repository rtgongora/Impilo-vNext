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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The four paediatric decision engines, reachable from the experience shell.
 *
 * <p>Until this controller existed the engines were deployed, live-proven and reachable only by
 * {@code curl}: the shell talks to the BFF and nothing in the BFF proxied any of them. This is
 * composition only — the clinical knowledge platform owns every judgement made here, and none of
 * these methods interprets, defaults or summarises a response.</p>
 *
 * <h2>Why these five failure modes are handled the way they are</h2>
 *
 * <p><strong>A failure is never an empty success.</strong> Every one of these engines answers a
 * question whose empty answer is an affirmative clinical claim. No classifications means <em>this
 * child has nothing wrong</em>; no danger signs means <em>this child was checked and is well</em>;
 * no due doses means <em>this child is up to date</em>; no growth signals means <em>this child is
 * growing normally</em>. Each is the most reassuring sentence the surface can show, and returning
 * it at the moment the system knew least is the defect that hid three broken verticals in this
 * programme. A downstream failure is a 502 with no {@code data} key, as the empty-200 honesty
 * register requires.</p>
 *
 * <p><strong>An engine's refusal is a success and is passed through.</strong> An
 * {@code INDETERMINATE} classification naming the observation it needs, an {@code incomplete}
 * danger-sign assessment listing what nobody looked at, a growth reading that is
 * {@code interpretation_blocked}, a dose that is {@code BLOCKED_BY_INTERVAL} — these are the
 * engines working, not failing. Converting any of them into an error or an empty result would
 * discard precisely the information the clinician needs, so a 2xx from CKP is returned verbatim
 * and a 4xx (a malformed request, or a forecast asked for without a date of birth) is passed
 * through with its own status and message rather than flattened to 502. A missing date of birth is
 * something the caller can fix; an unreachable engine is not, and a clinician must be able to tell
 * which they are looking at.</p>
 */
@RestController
@RequestMapping("/internal/v1/paediatric")
public class PaediatricDecisionSupportController {

    private static final Logger log = LoggerFactory.getLogger(PaediatricDecisionSupportController.class);

    private final ClinicalKnowledgePlatformClient ckpClient;

    public PaediatricDecisionSupportController(ClinicalKnowledgePlatformClient ckpClient) {
        this.ckpClient = ckpClient;
    }

    /**
     * Runs the IMNCI assess-and-classify tables. Age-routed by the engine between the child chart
     * and the sick-young-infant chart; the response reports which chart it used.
     */
    @PostMapping("/imnci/classify")
    public ResponseEntity<Map<String, Object>> imnciClassify(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = ckpClient.paediatricImnciClassify(body);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return passThroughOrUnavailable(e, "imnci_classification_unavailable",
                    "The IMNCI classification could not be obtained. This is not a classification "
                            + "of no illness — no chart was run against this child.",
                    requestId, correlationId);
        } catch (Exception e) {
            log.error("CKP IMNCI classify failed: {}", e.getMessage());
            return unavailable("imnci_classification_unavailable",
                    "The IMNCI classification could not be obtained. This is not a classification "
                            + "of no illness — no chart was run against this child.",
                    requestId, correlationId);
        }
    }

    /**
     * Forecasts the child's immunisation schedule from date of birth plus the doses already on the
     * record, which the caller supplies because the dose history belongs to pct-service.
     */
    @PostMapping("/immunisation/forecast")
    public ResponseEntity<Map<String, Object>> immunisationForecast(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = ckpClient.paediatricImmunisationForecast(body);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            // A 400 here is normally date_of_birth_required, which the caller can act on. Reporting
            // it as an outage would send someone to check the network over a missing field.
            return passThroughOrUnavailable(e, "immunisation_forecast_unavailable",
                    "The immunisation forecast could not be obtained. Do not read this as the child "
                            + "being up to date; no dose was evaluated.",
                    requestId, correlationId);
        } catch (Exception e) {
            log.error("CKP immunisation forecast failed: {}", e.getMessage());
            return unavailable("immunisation_forecast_unavailable",
                    "The immunisation forecast could not be obtained. Do not read this as the child "
                            + "being up to date; no dose was evaluated.",
                    requestId, correlationId);
        }
    }

    /**
     * Interprets a growth series. The caller supplies the measurements with the z-scores pct-service
     * stamped at the time, and the engine never recomputes one.
     */
    @PostMapping("/growth/interpret")
    public ResponseEntity<Map<String, Object>> growthInterpret(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = ckpClient.paediatricGrowthInterpret(body);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return passThroughOrUnavailable(e, "growth_interpretation_unavailable",
                    "The growth interpretation could not be obtained. An absence of signals here "
                            + "is not a finding that this child is growing normally.",
                    requestId, correlationId);
        } catch (Exception e) {
            log.error("CKP growth interpret failed: {}", e.getMessage());
            return unavailable("growth_interpretation_unavailable",
                    "The growth interpretation could not be obtained. An absence of signals here "
                            + "is not a finding that this child is growing normally.",
                    requestId, correlationId);
        }
    }

    /**
     * Evaluates the paediatric danger signs — ETAT emergency signs, IMNCI general danger signs and
     * the young-infant signs of possible serious bacterial infection.
     */
    @PostMapping("/danger-signs/evaluate")
    public ResponseEntity<Map<String, Object>> dangerSigns(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = ckpClient.paediatricDangerSigns(body);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return passThroughOrUnavailable(e, "danger_sign_evaluation_unavailable",
                    "The danger-sign evaluation could not be obtained. No sign was checked, which "
                            + "is not the same as no sign being present.",
                    requestId, correlationId);
        } catch (Exception e) {
            log.error("CKP paediatric danger signs failed: {}", e.getMessage());
            return unavailable("danger_sign_evaluation_unavailable",
                    "The danger-sign evaluation could not be obtained. No sign was checked, which "
                            + "is not the same as no sign being present.",
                    requestId, correlationId);
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        // A null body from a 2xx is not an empty assessment; it means the engine answered with
        // nothing at all, which no caller should render as a result.
        body.put("data", data);
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(body);
    }

    /**
     * A 4xx from CKP is the caller's to fix and keeps its status and message; anything else is an
     * outage and becomes a 502 saying what must not be inferred from it.
     */
    private ResponseEntity<Map<String, Object>> passThroughOrUnavailable(
            HttpStatusCodeException e, String errorCode, String message,
            String requestId, String correlationId) {
        if (e.getStatusCode().is4xxClientError()) {
            log.warn("CKP paediatric call refused with {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "paediatric_request_refused");
            body.put("status", e.getStatusCode().value());
            body.put("message", e.getResponseBodyAsString());
            body.put("meta", meta(requestId, correlationId));
            return ResponseEntity.status(e.getStatusCode()).body(body);
        }
        log.error("CKP paediatric call failed with {}: {}", e.getStatusCode(), e.getMessage());
        return unavailable(errorCode, message, requestId, correlationId);
    }

    /** 502 with no {@code data} key, per the empty-200 honesty register contract. */
    private static ResponseEntity<Map<String, Object>> unavailable(
            String error, String message, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of("request_id", requestId, "correlation_id", correlationId);
    }
}
