package zw.gov.mohcc.impilo.fhirgateway.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.fhirgateway.core.GatewayForwardService;
import zw.gov.mohcc.impilo.fhirgateway.core.GatewayRouteService;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirRouteEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;

@RestController
@RequestMapping("/internal/v1/gateway")
public class GatewayRouteController {

    private final GatewayRouteService routeService;
    private final GatewayForwardService forwardService;
    private final FhirAuditLogRepository auditLogRepository;

    public GatewayRouteController(GatewayRouteService routeService,
                                  GatewayForwardService forwardService,
                                  FhirAuditLogRepository auditLogRepository) {
        this.routeService = routeService;
        this.forwardService = forwardService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> listRoutes(
            @RequestParam(name = "tenantId", required = false) UUID tenantId) {
        UUID resolvedTenantId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<FhirRouteEntity> routes = routeService.listRoutes(resolvedTenantId);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "data", routes
        ));
    }

    @PostMapping("/routes")
    public ResponseEntity<Map<String, Object>> createRoute(@RequestBody CreateRouteRequest request) {
        UUID resolvedTenantId = request.tenantId() != null
                ? request.tenantId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        FhirRouteEntity route = routeService.createRoute(
                resolvedTenantId,
                request.correlationId(),
                request.sourceSystem(),
                request.resourceType(),
                request.targetEndpoint(),
                request.enabled());

        return ResponseEntity.status(201).body(Map.of(
                "status", "ok",
                "data", route
        ));
    }

    @PostMapping("/forward")
    public ResponseEntity<Map<String, Object>> forwardRequest(@RequestBody ForwardRequest request) {
        UUID resolvedTenantId = request.tenantId() != null
                ? request.tenantId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID correlationId = request.correlationId() != null
                ? request.correlationId()
                : UUID.randomUUID();

        GatewayForwardService.ForwardResult result = forwardService.forward(
                resolvedTenantId,
                request.actorId(),
                correlationId,
                request.sourceIp(),
                request.resourceType(),
                request.operation(),
                request.payload());

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "data", result
        ));
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> listAuditLogs(
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID resolvedTenantId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        Page<FhirAuditLogEntity> results = auditLogRepository.findByTenantId(
                resolvedTenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "data", results.getContent(),
                "page", page,
                "size", size,
                "totalElements", results.getTotalElements()
        ));
    }

    public record CreateRouteRequest(
            UUID tenantId,
            String correlationId,
            String sourceSystem,
            String resourceType,
            String targetEndpoint,
            Boolean enabled
    ) {}

    public record ForwardRequest(
            UUID tenantId,
            UUID correlationId,
            String actorId,
            String sourceIp,
            String resourceType,
            String operation,
            String payload
    ) {}
}
