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
import java.util.function.Supplier;

@RestController
@RequestMapping("/internal/v1/clinical/contraception/mec")
public class ContraceptionMecController {

    private static final Logger log = LoggerFactory.getLogger(ContraceptionMecController.class);
    private final ClinicalKnowledgePlatformClient ckp;

    public ContraceptionMecController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> assess(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ckp.mecAssess(body == null ? Map.of() : body), requestId, correlationId, "mec_unavailable");
    }

    private ResponseEntity<Map<String, Object>> proxy(Supplier<JsonNode> call, String requestId, String correlationId, String errorCode) {
        Map<String, Object> meta = Map.of("request_id", requestId, "correlation_id", correlationId);
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of("data", data == null ? Map.of() : data, "meta", meta));
        } catch (HttpStatusCodeException e) {
            log.warn("CKP MEC returned {}: {}", e.getStatusCode(), e.getStatusText());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("upstream_status", e.getStatusCode().value(), "meta", meta));
        } catch (Exception e) {
            log.error("CKP MEC call failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", errorCode, "meta", meta));
        }
    }
}
