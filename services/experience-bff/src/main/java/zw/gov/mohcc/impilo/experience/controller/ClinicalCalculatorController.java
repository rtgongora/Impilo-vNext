package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bedside clinical calculators, as the experience layer surfaces them.
 *
 * <p>A pass-through to the Clinical Knowledge Platform on purpose. The arithmetic lives in
 * {@code libs/medicine-domain} and is served by one endpoint; a copy here, or in the browser, would
 * be a second implementation to drift from the engines that read the same figures.</p>
 *
 * <h2>What must survive the hop</h2>
 * <p>A refusal is not an error. A calculator declining to score, with a named reason and a
 * clinician-readable explanation, is the instrument working, and it is returned as a 200 with that
 * text intact. A malformed value is a 400 and is passed through as one, so a form can mark the
 * offending field rather than showing a clinical explanation for a typo. Only a genuine failure to
 * reach the platform becomes a 502 — and it says so plainly, because a blank result where a number
 * was expected is the one outcome a clinician might read as a normal value.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/calculators")
public class ClinicalCalculatorController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalCalculatorController.class);

    private final ClinicalKnowledgePlatformClient ckp;

    public ClinicalCalculatorController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    /** The calculators the platform serves. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(envelope(ckp.listCalculators(), requestId, correlationId));
        } catch (Exception e) {
            log.error("CKP calculator catalogue unavailable: {}", e.getMessage());
            return unavailable(requestId, correlationId,
                    "The calculator catalogue could not be loaded.");
        }
    }

    /** One calculator's description: the fields a form renders, its source and its caveats. */
    @GetMapping("/{calculatorId}")
    public ResponseEntity<Map<String, Object>> describe(
            @PathVariable String calculatorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(
                    envelope(ckp.describeCalculator(calculatorId), requestId, correlationId));
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(requestId, correlationId,
                    "calculator_not_found",
                    "No calculator is served under '" + calculatorId + "'."));
        } catch (Exception e) {
            log.error("CKP calculator description unavailable id={}: {}", calculatorId, e.getMessage());
            return unavailable(requestId, correlationId,
                    "This calculator's inputs could not be loaded, so the form cannot be shown.");
        }
    }

    /**
     * Runs a calculator over submitted values.
     *
     * <p>The 400 branch is deliberate and must not be folded into the 502. A value the calculator
     * could not read is the caller's to fix and names the field; a platform that could not be
     * reached is not, and says nothing about the patient.</p>
     */
    @PostMapping("/{calculatorId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @PathVariable String calculatorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> inputs) {
        try {
            JsonNode data = ckp.evaluateCalculator(calculatorId, inputs);
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(requestId, correlationId,
                    "calculator_not_found",
                    "No calculator is served under '" + calculatorId + "'."));
        } catch (HttpClientErrorException.BadRequest e) {
            return ResponseEntity.badRequest().body(passThroughBadRequest(e, requestId, correlationId));
        } catch (Exception e) {
            log.error("CKP calculator evaluation failed id={}: {}", calculatorId, e.getMessage());
            return unavailable(requestId, correlationId,
                    "This calculator could not be run. No result is shown, because a blank where a"
                            + " number was expected must not be read as a normal value.");
        }
    }

    /**
     * Keeps the platform's field-level complaint rather than replacing it with a generic one.
     *
     * <p>"'one hundred' is not a number" tells a clinician which box to fix; "invalid request" sends
     * them hunting through a form of eight fields.</p>
     */
    private Map<String, Object> passThroughBadRequest(HttpClientErrorException e,
                                                      String requestId, String correlationId) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            if (body != null && body.has("error")) {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("error", body.get("error"));
                envelope.put("meta", meta(requestId, correlationId));
                return envelope;
            }
        } catch (Exception ignored) {
            // Fall through to the generic shape rather than turning a 400 into a 500.
        }
        return error(requestId, correlationId, "calculator_input_invalid",
                "One of the values could not be read. Check the numeric fields.");
    }

    private static Map<String, Object> envelope(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data);
        envelope.put("meta", meta(requestId, correlationId));
        return envelope;
    }

    private static ResponseEntity<Map<String, Object>> unavailable(String requestId,
                                                                   String correlationId,
                                                                   String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error(requestId, correlationId, "calculator_unavailable", message));
    }

    private static Map<String, Object> error(String requestId, String correlationId,
                                             String code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("error", code);
        envelope.put("message", message);
        envelope.put("meta", meta(requestId, correlationId));
        return envelope;
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of("request_id", requestId, "correlation_id", correlationId);
    }
}
