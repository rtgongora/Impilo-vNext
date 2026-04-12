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
@RequestMapping("/internal/v1/finance/payment-plans")
public class PaymentPlanFinanceBffController {

    private static final Logger log = LoggerFactory.getLogger(PaymentPlanFinanceBffController.class);

    private final CostaServiceClient costaClient;

    public PaymentPlanFinanceBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.postFinancePaymentPlan(body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA payment plan create failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam("patient_cpid") String patientCpid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePaymentPlans(patientCpid);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA payment plan list failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    @PutMapping("/{id}/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.putFinancePaymentPlanInstallment(id, body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA installment pay failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePaymentPlan(id);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA payment plan get failed: {}", e.getMessage());
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
