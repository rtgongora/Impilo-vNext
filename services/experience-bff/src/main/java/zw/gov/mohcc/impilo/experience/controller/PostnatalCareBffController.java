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

import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/internal/v1/clinical/maternal/pnc")
public class PostnatalCareBffController {

    private static final Logger log = LoggerFactory.getLogger(PostnatalCareBffController.class);
    private final ClinicalKnowledgePlatformClient ckp;

    public PostnatalCareBffController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    @PostMapping("/maternal/assess")
    public ResponseEntity<Map<String, Object>> assessMaternal(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ckp.pncMaternalAssess(body == null ? Map.of() : body), requestId, correlationId);
    }

    @PostMapping("/newborn/assess")
    public ResponseEntity<Map<String, Object>> assessNewborn(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ckp.pncNewbornAssess(body == null ? Map.of() : body), requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> proxy(Supplier<JsonNode> call, String requestId, String correlationId) {
        Map<String, Object> meta = Map.of("request_id", requestId, "correlation_id", correlationId);
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of("data", data == null ? Map.of() : data, "meta", meta));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("upstream_status", e.getStatusCode().value(), "meta", meta));
        } catch (Exception e) {
            log.error("CKP PNC call failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "pnc_assess_unavailable", "meta", meta));
        }
    }
}
