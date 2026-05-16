package zw.gov.mohcc.impilo.nhume.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.nhume.domain.FleetAssetEntity;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.repository.FleetAssetRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class NhumeFleetController {

    private final FleetAssetRepository repo;
    private final TrustLayerGuard trust;

    public NhumeFleetController(FleetAssetRepository repo, TrustLayerGuard trust) {
        this.repo = repo;
        this.trust = trust;
    }

    @GetMapping({"/api/v1/nhume/fleet", "/internal/v1/nhume/fleet"})
    public ResponseEntity<?> list(@RequestParam(required = false) String mode,
                                   @RequestParam(name = "availability", required = false) String availability,
                                   @RequestParam(name = "facility_ref", required = false) String facilityRef,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();
        Page<FleetAssetEntity> result = repo.findFiltered(
                UUID.fromString(ctx.tenantId()), mode, availability, facilityRef,
                PageRequest.of(page, Math.min(size, 200)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent().stream().map(this::toFullAsset).toList());
        body.put("page", page);
        body.put("size", result.getSize());
        body.put("total_elements", result.getTotalElements());
        body.put("has_more", result.hasNext());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/fleet/{id}", "/internal/v1/nhume/fleet/{id}"})
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return repo.findById(id).<ResponseEntity<?>>map(a -> ResponseEntity.ok(toFullAsset(a)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping({"/api/v1/nhume/fleet", "/internal/v1/nhume/fleet"})
    public ResponseEntity<?> create(@RequestBody FleetAssetPayload payload, HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "fleet.create");
        trust.requirePurposeOfUse(actor, "fleet.create");
        FleetAssetEntity a = new FleetAssetEntity();
        a.setAssetId(UUID.randomUUID());
        a.setTenantId(UUID.fromString(ctx.tenantId()));
        applyPayload(a, payload);
        repo.save(a);
        return ResponseEntity.status(HttpStatus.CREATED).body(toFullAsset(a));
    }

    @PatchMapping({"/api/v1/nhume/fleet/{id}", "/internal/v1/nhume/fleet/{id}"})
    public ResponseEntity<?> update(@PathVariable UUID id,
                                     @RequestBody FleetAssetPayload payload,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "fleet.update");
        return repo.findById(id).<ResponseEntity<?>>map(a -> {
            applyPayload(a, payload);
            a.setUpdatedAt(OffsetDateTime.now());
            repo.save(a);
            return ResponseEntity.ok(toFullAsset(a));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void applyPayload(FleetAssetEntity a, FleetAssetPayload p) {
        if (p.asset_code != null) a.setAssetCode(p.asset_code);
        if (p.display_name != null) a.setDisplayName(p.display_name);
        if (p.registration_number != null) a.setRegistrationNumber(p.registration_number);
        if (p.vehicle_type != null) a.setVehicleType(p.vehicle_type);
        if (p.mode_category != null) a.setModeCategory(p.mode_category);
        if (p.ownership != null) a.setOwnership(p.ownership);
        if (p.organisation_owner != null) a.setOrganisationOwner(p.organisation_owner);
        if (p.home_facility_ref != null) a.setHomeFacilityRef(p.home_facility_ref);
        if (p.assigned_programme != null) a.setAssignedProgramme(p.assigned_programme);
        if (p.cold_chain_capable != null) a.setColdChainCapable(p.cold_chain_capable);
        if (p.temperature_monitoring != null) a.setTemperatureMonitoring(p.temperature_monitoring);
        if (p.specimen_capable != null) a.setSpecimenCapable(p.specimen_capable);
        if (p.hazmat_capable != null) a.setHazmatCapable(p.hazmat_capable);
        if (p.gps_capable != null) a.setGpsCapable(p.gps_capable);
        if (p.telematics_provider != null) a.setTelematicsProvider(p.telematics_provider);
        if (p.maintenance_status != null) a.setMaintenanceStatus(p.maintenance_status);
        if (p.availability_status != null) a.setAvailabilityStatus(p.availability_status);
        if (p.operational_zone != null) a.setOperationalZone(p.operational_zone);
        if (p.active != null) a.setActive(p.active);
    }

    private Map<String, Object> toFullAsset(FleetAssetEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("asset_id", a.getAssetId());
        m.put("asset_code", a.getAssetCode());
        m.put("display_name", a.getDisplayName());
        m.put("registration_number", a.getRegistrationNumber());
        m.put("vehicle_type", a.getVehicleType());
        m.put("mode_category", a.getModeCategory());
        m.put("ownership", a.getOwnership());
        m.put("organisation_owner", a.getOrganisationOwner());
        m.put("home_facility_ref", a.getHomeFacilityRef());
        m.put("assigned_programme", a.getAssignedProgramme());
        m.put("capacity_units", a.getCapacityUnits());
        m.put("weight_limit_kg", a.getWeightLimitKg());
        m.put("volume_limit_l", a.getVolumeLimitL());
        m.put("cold_chain_capable", a.isColdChainCapable());
        m.put("temperature_monitoring", a.isTemperatureMonitoring());
        m.put("specimen_capable", a.isSpecimenCapable());
        m.put("hazmat_capable", a.isHazmatCapable());
        m.put("gps_capable", a.isGpsCapable());
        m.put("telematics_provider", a.getTelematicsProvider());
        m.put("maintenance_status", a.getMaintenanceStatus());
        m.put("availability_status", a.getAvailabilityStatus());
        m.put("operational_zone", a.getOperationalZone());
        m.put("last_lat", a.getLastLat());
        m.put("last_lng", a.getLastLng());
        m.put("last_seen_at", a.getLastSeenAt());
        m.put("active", a.isActive());
        m.put("is_demo", a.isDemo());
        m.put("autonomous_profile_json", a.getAutonomousProfileJson());
        return m;
    }

    public static class FleetAssetPayload {
        public String asset_code;
        public String display_name;
        public String registration_number;
        public String vehicle_type;
        public String mode_category;
        public String ownership;
        public String organisation_owner;
        public String home_facility_ref;
        public String assigned_programme;
        public Boolean cold_chain_capable;
        public Boolean temperature_monitoring;
        public Boolean specimen_capable;
        public Boolean hazmat_capable;
        public Boolean gps_capable;
        public String telematics_provider;
        public String maintenance_status;
        public String availability_status;
        public String operational_zone;
        public Boolean active;
    }
}
