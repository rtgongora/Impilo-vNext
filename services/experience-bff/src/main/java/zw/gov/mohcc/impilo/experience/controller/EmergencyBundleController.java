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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/internal/v1/clinical/maternal/emergency-bundles")
public class EmergencyBundleController {

    private static final Logger log = LoggerFactory.getLogger(EmergencyBundleController.class);

    private final ClinicalKnowledgePlatformClient ckp;

    public EmergencyBundleController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    public record AssessRequest(
            List<String> completedSteps,
            Long minutesSinceTrigger,
            Long minutesSinceLastObservation,
            Boolean controlConfirmed,
            Boolean clinicianConfirmedClose) {
    }

    @PostMapping("/pph/assess")
    public ResponseEntity<Map<String, Object>> assessPph(
            @RequestBody(required = false) AssessRequest request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ckp.emergencyBundleAssessPph(toBody(request)), requestId, correlationId,
                "pph_bundle_unavailable",
                "The PPH first-response bundle could not be evaluated. This is not a statement that "
                        + "the emergency is resolved. Act clinically and resubmit when the service returns.");
    }

    @PostMapping("/eclampsia/assess")
    public ResponseEntity<Map<String, Object>> assessEclampsia(
            @RequestBody(required = false) AssessRequest request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ckp.emergencyBundleAssessEclampsia(toBody(request)), requestId, correlationId,
                "eclampsia_bundle_unavailable",
                "The eclampsia first-response bundle could not be evaluated. This is not a statement that "
                        + "the emergency is resolved. Act clinically and resubmit when the service returns.");
    }

    private static Map<String, Object> toBody(AssessRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.completedSteps() != null) {
            body.put("completedSteps", request.completedSteps());
        }
        if (request.minutesSinceTrigger() != null) {
            body.put("minutesSinceTrigger", request.minutesSinceTrigger());
        }
        if (request.minutesSinceLastObservation() != null) {
            body.put("minutesSinceLastObservation", request.minutesSinceLastObservation());
        }
        if (request.controlConfirmed() != null) {
            body.put("controlConfirmed", request.controlConfirmed());
        }
        if (request.clinicianConfirmedClose() != null) {
            body.put("clinicianConfirmedClose", request.clinicianConfirmedClose());
        }
        return body;
    }

    private ResponseEntity<Map<String, Object>> proxy(Supplier<JsonNode> call,
                                                      String requestId,
                                                      String correlationId,
                                                      String unavailableCode,
                                                      String unavailableMessage) {
        Map<String, Object> meta = Map.of(
                "request_id", requestId,
                "correlation_id", correlationId);
        try {
            JsonNode data = call.get();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", data == null ? Map.of() : data);
            body.put("meta", meta);
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException e) {
            log.warn("CKP emergency-bundle returned {}: {}", e.getStatusCode(), e.getStatusText());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("upstream_status", e.getStatusCode().value());
            body.put("upstream_body", e.getResponseBodyAsString());
            body.put("meta", meta);
            return ResponseEntity.status(e.getStatusCode()).body(body);
        } catch (Exception e) {
            log.error("CKP emergency-bundle call failed: {}", e.getMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", unavailableCode);
            body.put("message", unavailableMessage);
            body.put("meta", meta);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
    }
}
