package zw.gov.mohcc.impilo.nhume.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DispatchZoneEntity;
import zw.gov.mohcc.impilo.nhume.domain.FleetAssetEntity;
import zw.gov.mohcc.impilo.nhume.integration.ndila.NdilaClient;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryExceptionRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryRequestRepository;
import zw.gov.mohcc.impilo.nhume.repository.DispatchZoneRepository;
import zw.gov.mohcc.impilo.nhume.repository.DriverCourierProfileRepository;
import zw.gov.mohcc.impilo.nhume.repository.FleetAssetRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated operational read endpoints for the Nhume web dashboard and
 * dispatcher console. All counts are tenant-scoped via the v1.2 trust headers.
 */
@RestController
@RequestMapping
public class NhumeDashboardController {

    private final DeliveryRequestRepository deliveryRepo;
    private final FleetAssetRepository assetRepo;
    private final DriverCourierProfileRepository courierRepo;
    private final DeliveryExceptionRepository exceptionRepo;
    private final DispatchZoneRepository zoneRepo;
    private final NdilaClient ndila;

    public NhumeDashboardController(DeliveryRequestRepository deliveryRepo,
                                     FleetAssetRepository assetRepo,
                                     DriverCourierProfileRepository courierRepo,
                                     DeliveryExceptionRepository exceptionRepo,
                                     DispatchZoneRepository zoneRepo,
                                     NdilaClient ndila) {
        this.deliveryRepo = deliveryRepo;
        this.assetRepo = assetRepo;
        this.courierRepo = courierRepo;
        this.exceptionRepo = exceptionRepo;
        this.zoneRepo = zoneRepo;
        this.ndila = ndila;
    }

    @GetMapping({"/api/v1/nhume/dashboard", "/internal/v1/nhume/dashboard"})
    public ResponseEntity<?> dashboard() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("generated_at", now);
        body.put("tenant_id", tenantId);
        body.put("counters", counters(tenantId, now));
        body.put("sla_breaches", deliveryRepo.findSlaBreached(tenantId, now)
                .stream().limit(20).map(this::summarize).toList());
        body.put("fleet_snapshot", fleetSnapshot(tenantId));
        body.put("courier_snapshot", courierSnapshot(tenantId));
        body.put("map_provider", ndila.mapProvider());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/dispatcher-console",
            "/internal/v1/nhume/dispatcher-console"})
    public ResponseEntity<?> dispatcherConsole() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pending", deliveryRepo.findFiltered(tenantId, "SUBMITTED", null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent()
                .stream().map(this::summarize).toList());
        body.put("dispatch_pending", deliveryRepo.findFiltered(tenantId, "DISPATCH_PENDING",
                null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent()
                .stream().map(this::summarize).toList());
        body.put("in_transit", deliveryRepo.findFiltered(tenantId, "IN_TRANSIT", null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent()
                .stream().map(this::summarize).toList());
        body.put("active_assets", assetRepo.findAvailable(tenantId)
                .stream().limit(50).map(this::assetSummary).toList());
        body.put("on_duty_couriers", courierRepo.findOnDuty(tenantId)
                .stream().limit(50).map(c -> Map.of(
                        "courier_id", c.getCourierId(),
                        "courier_code", c.getCourierCode(),
                        "display_name", c.getDisplayName(),
                        "kind", c.getCourierKind(),
                        "phone", c.getPhone() == null ? "" : c.getPhone()
                )).toList());
        body.put("map_provider", ndila.mapProvider());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/zones", "/internal/v1/nhume/zones"})
    public ResponseEntity<?> zones() {
        RequestContext ctx = RequestContextHolder.require();
        List<DispatchZoneEntity> zones = zoneRepo
                .findByTenantIdAndActiveTrue(UUID.fromString(ctx.tenantId()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", zones.stream().map(z -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("zone_id", z.getZoneId());
            m.put("code", z.getCode());
            m.put("name", z.getName());
            m.put("zone_kind", z.getZoneKind());
            m.put("geometry_kind", z.getGeometryKind());
            m.put("centre_lat", z.getCentreLat());
            m.put("centre_lng", z.getCentreLng());
            m.put("radius_m", z.getRadiusM());
            m.put("jurisdiction_ref", z.getJurisdictionRef());
            return m;
        }).toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/map-token", "/internal/v1/nhume/map-token"})
    public ResponseEntity<?> mapToken() {
        NdilaClient.NdilaTileToken token = ndila.issueTileToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", token.provider());
        body.put("token", token.token());
        body.put("style_url", token.styleUrl());
        body.put("expires_at_epoch_ms", token.expiresAtEpochMs());
        return ResponseEntity.ok(body);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> counters(UUID tenantId, OffsetDateTime now) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("total", deliveryRepo.countByTenantId(tenantId));
        for (String s : new String[]{"SUBMITTED", "DISPATCH_PENDING", "ASSIGNED",
                "ACCEPTED", "EN_ROUTE_TO_PICKUP", "PICKED_UP", "IN_TRANSIT",
                "DELIVERED", "FAILED", "RETURNED", "CANCELLED"}) {
            c.put(s.toLowerCase(), deliveryRepo.countByTenantIdAndStatus(tenantId, s));
        }
        c.put("active_couriers", courierRepo.countByTenantIdAndCurrentStatus(tenantId, "ON_DUTY"));
        c.put("total_couriers", courierRepo.countByTenantIdAndActiveTrue(tenantId));
        c.put("active_assets", assetRepo.countByTenantIdAndActiveTrue(tenantId));
        c.put("open_exceptions", exceptionRepo.countByResolvedAtIsNull());
        c.put("sla_breaches", deliveryRepo.findSlaBreached(tenantId, now).size());
        return c;
    }

    private Map<String, Object> fleetSnapshot(UUID tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<FleetAssetEntity> available = assetRepo.findAvailable(tenantId);
        m.put("available_count", available.size());
        m.put("available", available.stream().limit(25).map(this::assetSummary).toList());
        return m;
    }

    private Map<String, Object> courierSnapshot(UUID tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("on_duty_count", courierRepo.countByTenantIdAndCurrentStatus(tenantId, "ON_DUTY"));
        m.put("on_duty", courierRepo.findOnDuty(tenantId).stream().limit(25).map(c -> Map.of(
                "courier_id", c.getCourierId(),
                "display_name", c.getDisplayName(),
                "kind", c.getCourierKind()
        )).toList());
        return m;
    }

    private Map<String, Object> summarize(DeliveryRequestEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("delivery_id", d.getDeliveryId());
        m.put("reference_code", d.getReferenceCode());
        m.put("status", d.getStatus());
        m.put("priority", d.getPriority());
        m.put("delivery_type", d.getDeliveryType());
        m.put("origin_label", d.getOriginLabel());
        m.put("destination_label", d.getDestinationLabel());
        m.put("recipient_name", d.getRecipientName());
        m.put("requesting_facility_id", d.getRequestingFacilityId());
        m.put("sla_due_at", d.getSlaDueAt());
        m.put("created_at", d.getCreatedAt());
        return m;
    }

    private Map<String, Object> assetSummary(FleetAssetEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("asset_id", a.getAssetId());
        m.put("asset_code", a.getAssetCode());
        m.put("display_name", a.getDisplayName());
        m.put("vehicle_type", a.getVehicleType());
        m.put("mode_category", a.getModeCategory());
        m.put("availability_status", a.getAvailabilityStatus());
        m.put("home_facility_ref", a.getHomeFacilityRef());
        m.put("last_lat", a.getLastLat());
        m.put("last_lng", a.getLastLng());
        m.put("last_seen_at", a.getLastSeenAt());
        m.put("cold_chain_capable", a.isColdChainCapable());
        return m;
    }
}
