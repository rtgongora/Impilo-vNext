package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.postFinanceDocumentGenerate(body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA document generate failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam String subject_ref,
            @RequestParam(required = false) String subject_type,
            @RequestParam(required = false) String doc_type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinanceDocuments(subject_ref, subject_type, doc_type, page, size);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA document list failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinanceDocument(id);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA document get failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("meta", Map.of("correlation_id", correlationId));
        return out;
    }

    private static Map<String, Object> error(String message, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", null);
        out.put("error", Map.of("message", message, "correlation_id", correlationId));
        return out;
    }
}
