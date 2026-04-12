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

/**
 * BFF proxy for COSTA patient account / transaction endpoints.
 */
@RestController
@RequestMapping("/internal/v1/finance/patient-accounts")
public class PatientAccountFinanceBffController {

    private static final Logger log = LoggerFactory.getLogger(PatientAccountFinanceBffController.class);

    private final CostaServiceClient costaClient;

    public PatientAccountFinanceBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @GetMapping("/{cpid}")
    public ResponseEntity<Map<String, Object>> getAccount(
            @PathVariable String cpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePatientAccount(cpid);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA patient account fetch failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping("/{cpid}/transactions")
    public ResponseEntity<Map<String, Object>> listTransactions(
            @PathVariable String cpid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePatientTransactions(cpid, page, size, dateFrom, dateTo);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA patient transactions fetch failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping("/{cpid}/outstanding")
    public ResponseEntity<Map<String, Object>> outstanding(
            @PathVariable String cpid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePatientOutstanding(cpid);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA outstanding bills fetch failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    @PostMapping("/{cpid}/payments")
    public ResponseEntity<Map<String, Object>> recordPayment(
            @PathVariable String cpid,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.postFinancePatientPayment(cpid, body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA record payment failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error(e.getMessage(), correlationId));
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
