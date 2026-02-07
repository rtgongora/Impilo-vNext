package zw.gov.mohcc.impilo.oros.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.CapabilityDto;
import zw.gov.mohcc.impilo.oros.core.CapabilityService;
import zw.gov.mohcc.impilo.oros.core.OrderStateMachine;
import zw.gov.mohcc.impilo.oros.core.ResultService;
import zw.gov.mohcc.impilo.oros.integration.ButanoIntegration;
import zw.gov.mohcc.impilo.oros.integration.PctIntegration;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for internal/operational endpoints.
 *
 * <p>Provides hooks for triggering BUTANO writebacks, PCT notifications,
 * and listing facility capabilities. These endpoints are intended for
 * operational use and internal service-to-service communication.</p>
 */
@RestController
@RequestMapping("/v1/internal")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final ButanoIntegration butanoIntegration;
    private final PctIntegration pctIntegration;
    private final CapabilityService capabilityService;
    private final OrderStateMachine stateMachine;
    private final ResultService resultService;

    public InternalController(ButanoIntegration butanoIntegration,
                              PctIntegration pctIntegration,
                              CapabilityService capabilityService,
                              OrderStateMachine stateMachine,
                              ResultService resultService) {
        this.butanoIntegration = butanoIntegration;
        this.pctIntegration = pctIntegration;
        this.capabilityService = capabilityService;
        this.stateMachine = stateMachine;
        this.resultService = resultService;
    }

    /**
     * Trigger a BUTANO writeback for an order.
     * Creates a FHIR ServiceRequest in the shared health record.
     */
    @PostMapping("/butano/writeback")
    public ResponseEntity<ApiResponse<Map<String, String>>> butanoWriteback(
            @RequestBody Map<String, String> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String orderId = request.get("orderId");

        if (orderId == null || orderId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("OROS_MISSING_ORDER_ID",
                            "orderId is required", 400, correlationId));
        }

        OrderEntity order = stateMachine.getOrder(orderId);
        String butanoRef = butanoIntegration.createServiceRequest(order);

        Map<String, String> response = Map.of(
                "orderId", orderId,
                "butanoRef", butanoRef != null ? butanoRef : "UNAVAILABLE"
        );

        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Trigger a PCT integration hook for an order.
     * Notifies PCT about expected worksteps or available results.
     */
    @PostMapping("/pct/hook")
    public ResponseEntity<ApiResponse<Map<String, String>>> pctHook(
            @RequestBody Map<String, String> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String orderId = request.get("orderId");
        String action = request.getOrDefault("action", "notify_result");

        if (orderId == null || orderId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("OROS_MISSING_ORDER_ID",
                            "orderId is required", 400, correlationId));
        }

        OrderEntity order = stateMachine.getOrder(orderId);

        if ("notify_result".equals(action)) {
            List<ResultEntity> results = resultService.getResults(orderId);
            if (!results.isEmpty()) {
                pctIntegration.notifyResultAvailable(order, results.get(0));
            }
        }

        Map<String, String> response = Map.of(
                "orderId", orderId,
                "action", action,
                "status", "dispatched"
        );

        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * List all active capabilities for the current tenant.
     */
    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<List<CapabilityDto>>> listCapabilities() {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<CapabilityDto> capabilities = capabilityService.listCapabilities().stream()
                .map(CapabilityDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(capabilities, correlationId));
    }
}
