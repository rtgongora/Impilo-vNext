package zw.gov.mohcc.impilo.nhume.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.nhume.domain.AutonomousMissionEntity;
import zw.gov.mohcc.impilo.nhume.integration.autonomous.AutonomousAdapterRegistry;
import zw.gov.mohcc.impilo.nhume.integration.autonomous.AutonomousDeliveryAdapter;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.repository.AutonomousMissionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Autonomous mission endpoints — drone / UAV / AGV / robot / smart locker.
 *
 * <p>All providers run through the adapter registry. The default simulation
 * adapter returns clearly-labelled mock telemetry; production providers plug
 * in via {@link AutonomousDeliveryAdapter} beans.
 */
@RestController
@RequestMapping
public class NhumeAutonomousController {

    private final AutonomousMissionRepository repo;
    private final AutonomousAdapterRegistry adapters;
    private final TrustLayerGuard trust;

    public NhumeAutonomousController(AutonomousMissionRepository repo,
                                      AutonomousAdapterRegistry adapters,
                                      TrustLayerGuard trust) {
        this.repo = repo;
        this.adapters = adapters;
        this.trust = trust;
    }

    @GetMapping({"/api/v1/nhume/autonomous-missions",
            "/internal/v1/nhume/autonomous-missions"})
    public ResponseEntity<?> list(@RequestParam(required = false) String status,
                                   @RequestParam(name = "provider_code", required = false) String providerCode,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();
        Page<AutonomousMissionEntity> result = repo.findFiltered(
                UUID.fromString(ctx.tenantId()), status, providerCode,
                PageRequest.of(page, Math.min(size, 200)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent().stream().map(this::toMission).toList());
        body.put("page", page);
        body.put("size", result.getSize());
        body.put("total_elements", result.getTotalElements());
        body.put("has_more", result.hasNext());
        return ResponseEntity.ok(body);
    }

    @PostMapping({"/api/v1/nhume/autonomous-missions",
            "/internal/v1/nhume/autonomous-missions"})
    @Transactional
    public ResponseEntity<?> create(@RequestBody MissionPayload payload,
                                     HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "autonomous.create");
        trust.requirePurposeOfUse(actor, "autonomous.create");
        AutonomousDeliveryAdapter adapter = adapters.resolve(payload.provider_code);
        AutonomousDeliveryAdapter.MissionAck ack = adapter.createMission(
                new AutonomousDeliveryAdapter.MissionRequest(
                        payload.provider_code,
                        payload.delivery_id != null ? payload.delivery_id.toString() : null,
                        payload.launch_site, payload.landing_site,
                        payload.scheduled_for,
                        Map.of("source", "nhume", "kind", payload.mission_kind == null
                                ? "DRONE" : payload.mission_kind)));
        AutonomousMissionEntity m = new AutonomousMissionEntity();
        m.setMissionId(UUID.randomUUID());
        m.setTenantId(UUID.fromString(ctx.tenantId()));
        m.setDeliveryId(payload.delivery_id);
        m.setAssetId(payload.asset_id);
        m.setProviderCode(payload.provider_code);
        if (payload.mission_kind != null) m.setMissionKind(payload.mission_kind);
        m.setLaunchSite(payload.launch_site);
        m.setLandingSite(payload.landing_site);
        m.setScheduledFor(payload.scheduled_for);
        m.setStatus(ack.status() == null ? "ACCEPTED" : ack.status());
        m.setSimulationMode("SIMULATION".equalsIgnoreCase(adapter.providerKind()));
        m.setTelemetryJson(safe(ack.telemetry()));
        repo.save(m);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMission(m));
    }

    @PostMapping({"/api/v1/nhume/autonomous-missions/{id}/cancel",
            "/internal/v1/nhume/autonomous-missions/{id}/cancel"})
    @Transactional
    public ResponseEntity<?> cancel(@PathVariable UUID id,
                                     @RequestBody(required = false) CancelPayload payload,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "autonomous.cancel");
        return repo.findById(id).<ResponseEntity<?>>map(m -> {
            AutonomousDeliveryAdapter adapter = adapters.resolve(m.getProviderCode());
            adapter.cancelMission(m.getMissionId().toString(),
                    payload != null && payload.reason != null ? payload.reason : "user-requested");
            m.setStatus("CANCELLED");
            m.setUpdatedAt(OffsetDateTime.now());
            repo.save(m);
            return ResponseEntity.ok(toMission(m));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping({"/api/v1/nhume/autonomous-missions/{id}",
            "/internal/v1/nhume/autonomous-missions/{id}"})
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return repo.findById(id).<ResponseEntity<?>>map(m -> {
            AutonomousDeliveryAdapter adapter = adapters.resolve(m.getProviderCode());
            AutonomousDeliveryAdapter.MissionStatus status =
                    adapter.getMissionStatus(m.getMissionId().toString());
            Map<String, Object> body = new LinkedHashMap<>(toMission(m));
            body.put("live_status", Map.of(
                    "status", status.status(),
                    "status_at", status.statusAt(),
                    "telemetry", status.telemetry()
            ));
            return ResponseEntity.ok(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toMission(AutonomousMissionEntity m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mission_id", m.getMissionId());
        map.put("delivery_id", m.getDeliveryId());
        map.put("asset_id", m.getAssetId());
        map.put("provider_code", m.getProviderCode());
        map.put("mission_kind", m.getMissionKind());
        map.put("status", m.getStatus());
        map.put("launch_site", m.getLaunchSite());
        map.put("landing_site", m.getLandingSite());
        map.put("scheduled_for", m.getScheduledFor());
        map.put("launched_at", m.getLaunchedAt());
        map.put("arrived_at", m.getArrivedAt());
        map.put("completed_at", m.getCompletedAt());
        map.put("simulation_mode", m.isSimulationMode());
        // OF-B20 §12.9 — corridor linkage + governed-fallback reason (journey #67).
        // A NOT_FLOWN status is the ONLY honest flight posture on this estate.
        map.put("corridor_code", m.getCorridorCode());
        map.put("fallback_reason", m.getFallbackReason());
        map.put("telemetry_json", m.getTelemetryJson());
        map.put("created_at", m.getCreatedAt());
        return map;
    }

    private String safe(Map<String, Object> t) {
        if (t == null || t.isEmpty()) return "{}";
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(t); }
        catch (Exception e) { return "{}"; }
    }

    public static class MissionPayload {
        public String provider_code;
        public UUID delivery_id;
        public UUID asset_id;
        public String mission_kind;
        public String launch_site;
        public String landing_site;
        public OffsetDateTime scheduled_for;
    }

    public static class CancelPayload {
        public String reason;
    }
}
