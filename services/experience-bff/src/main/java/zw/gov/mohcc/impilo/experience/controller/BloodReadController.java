package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MadiServiceClient;

import java.util.Map;

/**
 * Blood services read-paths for the blood-services-chain journey.
 *
 * <p>GET /internal/v1/blood/inventory — national blood availability when no bank is supplied
 * (MADI central-bank metrics), or per-bank stock when {@code blood_bank_id} is provided. The
 * MADI namespace ({@code /internal/v1/madi/**}) remains the operator surface; this controller
 * exposes the canonical {@code /internal/v1/blood} read path used by the journey proofs.</p>
 */
@RestController
@RequestMapping("/internal/v1/blood")
public class BloodReadController {

    private final MadiServiceClient madiClient;

    public BloodReadController(MadiServiceClient madiClient) {
        this.madiClient = madiClient;
    }

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> inventory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "blood_bank_id", required = false) String bloodBankId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode data = (bloodBankId != null && !bloodBankId.isBlank())
                    ? madiClient.bloodBankStock(bloodBankId)
                    : madiClient.centralBankMetrics();
            Map<String, Object> meta = Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId,
                    "scope", (bloodBankId != null && !bloodBankId.isBlank()) ? "blood-bank" : "national");
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "BLOOD_INVENTORY_UNAVAILABLE",
                            "message", e.getMessage() != null ? e.getMessage() : "Blood inventory upstream unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
