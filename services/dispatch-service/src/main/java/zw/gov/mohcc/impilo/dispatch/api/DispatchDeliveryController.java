package zw.gov.mohcc.impilo.dispatch.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.dispatch.core.DeliveryOperationsService;
import zw.gov.mohcc.impilo.dispatch.domain.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/dispatch")
public class DispatchDeliveryController {

    private final DeliveryOperationsService deliveryService;

    public DispatchDeliveryController(DeliveryOperationsService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/deliveries")
    public ResponseEntity<?> listDeliveries() {
        RequestContext ctx = RequestContextHolder.require();
        List<Map<String, Object>> items = deliveryService.listDeliveries(UUID.fromString(ctx.tenantId()))
                .stream().map(deliveryService::toDeliveryView).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PostMapping("/deliveries")
    public ResponseEntity<?> createDelivery(@RequestBody Map<String, Object> body, jakarta.servlet.http.HttpServletRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        DeliveryRequestEntity created = deliveryService.createDelivery(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                request.getHeader(CompanionHeaders.IDEMPOTENCY_KEY),
                body
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.toDeliveryView(created));
    }

    @GetMapping("/deliveries/{id}")
    public ResponseEntity<?> getDelivery(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.toDeliveryView(deliveryService.getDelivery(id)));
    }

    @PatchMapping("/deliveries/{id}")
    public ResponseEntity<?> patchDelivery(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(deliveryService.toDeliveryView(deliveryService.patchDelivery(id, body)));
    }

    @PostMapping("/deliveries/{id}/{action}")
    public ResponseEntity<?> deliveryAction(
            @PathVariable UUID id,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            jakarta.servlet.http.HttpServletRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        Map<String, Object> payload = body != null ? body : Map.of();
        DeliveryRequestEntity updated = deliveryService.performAction(
                id,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                request.getHeader(CompanionHeaders.IDEMPOTENCY_KEY),
                action,
                payload
        );
        return ResponseEntity.ok(deliveryService.toDeliveryView(updated));
    }

    @GetMapping("/deliveries/{id}/tracking")
    public ResponseEntity<?> tracking(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.getTracking(id));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(deliveryService.dashboard(UUID.fromString(ctx.tenantId())));
    }

    @GetMapping("/dispatcher-console")
    public ResponseEntity<?> dispatcherConsole() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(deliveryService.dispatcherConsole(UUID.fromString(ctx.tenantId())));
    }

    @GetMapping("/fleet")
    public ResponseEntity<?> listFleet() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", deliveryService.listFleet(UUID.fromString(ctx.tenantId())).stream().map(deliveryService::toFleetView).toList()));
    }

    @PostMapping("/fleet")
    public ResponseEntity<?> createFleet(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        FleetAssetEntity asset = deliveryService.createFleet(UUID.fromString(ctx.tenantId()), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.toFleetView(asset));
    }

    @GetMapping("/fleet/{id}")
    public ResponseEntity<?> getFleet(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.toFleetView(deliveryService.getFleet(id)));
    }

    @PatchMapping("/fleet/{id}")
    public ResponseEntity<?> patchFleet(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(deliveryService.toFleetView(deliveryService.patchFleet(id, body)));
    }

    @GetMapping("/couriers")
    public ResponseEntity<?> listCouriers() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", deliveryService.listCouriers(UUID.fromString(ctx.tenantId())).stream().map(deliveryService::toCourierView).toList()));
    }

    @PostMapping("/couriers")
    public ResponseEntity<?> createCouriers(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        DriverCourierProfileEntity courier = deliveryService.createCourier(UUID.fromString(ctx.tenantId()), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.toCourierView(courier));
    }

    @GetMapping("/couriers/{id}")
    public ResponseEntity<?> getCourier(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.toCourierView(deliveryService.getCourier(id)));
    }

    @PatchMapping("/couriers/{id}")
    public ResponseEntity<?> patchCourier(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(deliveryService.toCourierView(deliveryService.patchCourier(id, body)));
    }

    @GetMapping("/zones")
    public ResponseEntity<?> listZones() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", deliveryService.listZones(UUID.fromString(ctx.tenantId()))));
    }

    @PostMapping("/zones")
    public ResponseEntity<?> createZone(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        DispatchZoneEntity zone = deliveryService.createZone(UUID.fromString(ctx.tenantId()), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(zone);
    }

    @GetMapping("/policies")
    public ResponseEntity<?> listPolicies() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", deliveryService.listPolicies(UUID.fromString(ctx.tenantId()))));
    }

    @PostMapping("/policies")
    public ResponseEntity<?> createPolicy(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        DeliveryPolicyEntity policy = deliveryService.createPolicy(UUID.fromString(ctx.tenantId()), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(policy);
    }

    @GetMapping("/integrations")
    public ResponseEntity<?> listIntegrations() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", deliveryService.listIntegrations(UUID.fromString(ctx.tenantId()))));
    }

    @PostMapping("/integrations")
    public ResponseEntity<?> createIntegrations(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        DeliveryIntegrationProviderEntity provider = deliveryService.createIntegration(UUID.fromString(ctx.tenantId()), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(provider);
    }

    @GetMapping("/autonomous-missions")
    public ResponseEntity<?> listMissions() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", deliveryService.listMissions(UUID.fromString(ctx.tenantId())).stream().map(deliveryService::toMissionView).toList()));
    }

    @PostMapping("/autonomous-missions")
    public ResponseEntity<?> createMission(@RequestBody Map<String, Object> body, jakarta.servlet.http.HttpServletRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        AutonomousMissionEntity mission = deliveryService.createMission(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                request.getHeader(CompanionHeaders.IDEMPOTENCY_KEY),
                body
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.toMissionView(mission));
    }

    @PostMapping("/webhooks/{providerCode}")
    public ResponseEntity<?> webhook(@PathVariable String providerCode,
                                     @RequestBody Map<String, Object> body,
                                     jakarta.servlet.http.HttpServletRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(deliveryService.handleWebhook(
                UUID.fromString(ctx.tenantId()),
                providerCode,
                ctx.podId(),
                ctx.correlationId(),
                request.getHeader(CompanionHeaders.IDEMPOTENCY_KEY),
                body));
    }
}
