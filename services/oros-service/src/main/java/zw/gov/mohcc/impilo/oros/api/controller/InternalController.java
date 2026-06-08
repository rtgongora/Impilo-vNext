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
import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    @PostMapping({"/pct/hook", "/pct/order-hook"})
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

    /**
     * Create or update facility capability configuration.
     */
    @PutMapping("/capabilities")
    public ResponseEntity<ApiResponse<CapabilityDto>> upsertCapability(
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Object facilityRaw = request.get("facilityId");
        Object adapterRaw = request.get("adapterMode");
        Object orderTypesRaw = request.get("supportedOrderTypes");
        if (facilityRaw == null || adapterRaw == null || orderTypesRaw == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("OROS_INVALID_CAPABILITY",
                            "facilityId, adapterMode, and supportedOrderTypes are required", 400, correlationId));
        }

        UUID facilityId = UUID.fromString(facilityRaw.toString());
        AdapterMode adapterMode = AdapterMode.valueOf(adapterRaw.toString());
        @SuppressWarnings("unchecked")
        List<String> orderTypeNames = (List<String>) orderTypesRaw;
        String externalEndpoint = request.get("externalEndpoint") != null
                ? request.get("externalEndpoint").toString() : null;
        String config = request.get("routingRules") != null
                ? request.get("routingRules").toString() : null;

        CapabilityDto last = null;
        for (String orderTypeName : orderTypeNames) {
            var entity = capabilityService.upsertCapability(
                    facilityId, OrderType.valueOf(orderTypeName), adapterMode, externalEndpoint, config);
            last = CapabilityDto.from(entity);
        }
        return ResponseEntity.ok(ApiResponse.ok(last, correlationId));
    }
}
