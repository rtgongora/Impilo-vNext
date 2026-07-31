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
 * Bishop cervical favourability score — assessment only, nothing persisted.
 *
 * <p>Blank ≠ zero: a missing component stays omitted so CKP returns INCOMPLETE rather than inventing
 * a total.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/bishop")
public class BishopScoreController {

    private static final Logger log = LoggerFactory.getLogger(BishopScoreController.class);

    private static final String LINK_PREFIX = "bishop.";

    private static final Set<String> COMPONENT_FIELDS = Set.of(
            "dilation", "effacement", "station", "consistency", "position");

    private final ClinicalKnowledgePlatformClient ckp;

    public BishopScoreController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    public record AssessRequest(
            Object dilation,
            Object effacement,
            Object station,
            Object consistency,
            Object position) {
    }

    public record FormAssessRequest(String formKey, Map<String, Object> answers) {
    }

    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> assess(
            @RequestBody(required = false) AssessRequest request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ckp.bishopAssess(toBody(request)), requestId, correlationId);
    }

    @PostMapping("/classify-form")
    public ResponseEntity<Map<String, Object>> classifyFromForm(
            @RequestBody(required = false) FormAssessRequest request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> body = mapFormAnswers(request);
        return proxy(() -> ckp.bishopAssess(body), requestId, correlationId);
    }

    private static Map<String, Object> toBody(AssessRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, "dilation", request.dilation());
        putIfPresent(body, "effacement", request.effacement());
        putIfPresent(body, "station", request.station());
        putIfPresent(body, "consistency", request.consistency());
        putIfPresent(body, "position", request.position());
        return body;
    }

    private static Map<String, Object> mapFormAnswers(FormAssessRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> answers = request == null || request.answers() == null
                ? Map.of() : request.answers();

        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String field = entry.getKey() == null ? "" : entry.getKey().trim();
            if (field.startsWith(LINK_PREFIX)) {
                field = field.substring(LINK_PREFIX.length());
            }
            if (!COMPONENT_FIELDS.contains(field)) {
                continue;
            }
            Object raw = entry.getValue();
            if (isBlank(raw)) {
                continue;
            }
            body.put(field, raw);
        }
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (!isBlank(value)) {
            body.put(key, value);
        }
    }

    private static boolean isBlank(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private ResponseEntity<Map<String, Object>> proxy(Supplier<JsonNode> call,
                                                      String requestId,
                                                      String correlationId) {
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
            log.warn("CKP bishop score returned {}: {}", e.getStatusCode(), e.getStatusText());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("upstream_status", e.getStatusCode().value());
            body.put("upstream_body", e.getResponseBodyAsString());
            body.put("meta", meta);
            return ResponseEntity.status(e.getStatusCode()).body(body);
        } catch (Exception e) {
            log.error("CKP bishop score call failed: {}", e.getMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "bishop_score_unavailable");
            body.put("message",
                    "The Bishop score could not be evaluated. This is not a statement that the cervix is "
                            + "favourable. Assess clinically and resubmit when the service returns.");
            body.put("meta", meta);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
    }
}
