package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/finance/documents")
public class FinancialDocumentFinanceBffController {

    private static final Logger log = LoggerFactory.getLogger(FinancialDocumentFinanceBffController.class);

    private final CostaServiceClient costaClient;

    public FinancialDocumentFinanceBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.postFinanceDocumentGenerate(body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA document generate failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to generate financial document", requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam String subject_ref,
            @RequestParam(required = false) String subject_type,
            @RequestParam(required = false) String doc_type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinanceDocuments(subject_ref, subject_type, doc_type, page, size);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA document list failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to list financial documents", requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinanceDocument(id);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA document get failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to fetch financial document", requestId, correlationId);
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("meta", Map.of("correlation_id", correlationId));
        return out;
    }

    private static ResponseEntity<Map<String, Object>> failClose(String code, String message, String requestId, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", null);
        out.put("error", Map.of("code", code, "message", message));
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(out);
    }
}
