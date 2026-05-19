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
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.postFinancePaymentPlan(body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA payment plan create failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to create payment plan", requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam("patient_cpid") String patientCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePaymentPlans(patientCpid);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA payment plan list failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to list payment plans", requestId, correlationId);
        }
    }

    @PutMapping("/{id}/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.putFinancePaymentPlanInstallment(id, body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA installment pay failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to pay installment", requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = costaClient.getFinancePaymentPlan(id);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA payment plan get failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to fetch payment plan", requestId, correlationId);
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
