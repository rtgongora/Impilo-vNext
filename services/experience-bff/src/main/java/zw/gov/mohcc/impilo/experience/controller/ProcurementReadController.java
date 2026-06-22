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
import zw.gov.mohcc.impilo.experience.client.ProcurementServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Procurement read-paths for the enterprise / marketplace-procurement journey.
 *
 * <p>GET /internal/v1/procurement/requisitions — tenant-scoped procurement requisitions.
 * The ERP namespace ({@code /internal/v1/erp/procurement/**}) remains the operator surface;
 * this controller exposes the canonical {@code /internal/v1/procurement} read path consumed
 * by the cross-service journey proofs.</p>
 */
@RestController
@RequestMapping("/internal/v1/procurement")
public class ProcurementReadController {

    private final ProcurementServiceClient procurementClient;

    public ProcurementReadController(ProcurementServiceClient procurementClient) {
        this.procurementClient = procurementClient;
    }

    @GetMapping("/requisitions")
    public ResponseEntity<Map<String, Object>> listRequisitions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode data = procurementClient.getRequisitions();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "PROCUREMENT_UNAVAILABLE",
                            "message", e.getMessage() != null ? e.getMessage() : "Procurement upstream unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
