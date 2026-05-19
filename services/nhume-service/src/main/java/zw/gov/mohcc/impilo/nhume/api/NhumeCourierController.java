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
import zw.gov.mohcc.impilo.nhume.domain.DriverCourierProfileEntity;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.repository.DriverCourierProfileRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class NhumeCourierController {

    private final DriverCourierProfileRepository repo;
    private final TrustLayerGuard trust;

    public NhumeCourierController(DriverCourierProfileRepository repo, TrustLayerGuard trust) {
        this.repo = repo;
        this.trust = trust;
    }

    @GetMapping({"/api/v1/nhume/couriers", "/internal/v1/nhume/couriers"})
    public ResponseEntity<?> list(@RequestParam(required = false) String kind,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();
        Page<DriverCourierProfileEntity> result = repo.findFiltered(
                UUID.fromString(ctx.tenantId()), kind, status,
                PageRequest.of(page, Math.min(size, 200)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent().stream().map(this::toCourier).toList());
        body.put("page", page);
        body.put("size", result.getSize());
        body.put("total_elements", result.getTotalElements());
        body.put("has_more", result.hasNext());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/couriers/{id}", "/internal/v1/nhume/couriers/{id}"})
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return repo.findById(id).<ResponseEntity<?>>map(c -> ResponseEntity.ok(toCourier(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping({"/api/v1/nhume/couriers", "/internal/v1/nhume/couriers"})
    public ResponseEntity<?> create(@RequestBody CourierPayload p, HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "courier.create");
        trust.requirePurposeOfUse(actor, "courier.create");
        DriverCourierProfileEntity c = new DriverCourierProfileEntity();
        c.setCourierId(UUID.randomUUID());
        c.setTenantId(UUID.fromString(ctx.tenantId()));
        applyPayload(c, p);
        repo.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCourier(c));
    }

    @PatchMapping({"/api/v1/nhume/couriers/{id}", "/internal/v1/nhume/couriers/{id}"})
    public ResponseEntity<?> update(@PathVariable UUID id,
                                     @RequestBody CourierPayload p,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "courier.update");
        return repo.findById(id).<ResponseEntity<?>>map(c -> {
            applyPayload(c, p);
            c.setUpdatedAt(OffsetDateTime.now());
            repo.save(c);
            return ResponseEntity.ok(toCourier(c));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void applyPayload(DriverCourierProfileEntity c, CourierPayload p) {
        if (p.courier_code != null) c.setCourierCode(p.courier_code);
        if (p.display_name != null) c.setDisplayName(p.display_name);
        if (p.courier_kind != null) c.setCourierKind(p.courier_kind);
        if (p.phone != null) c.setPhone(p.phone);
        if (p.email != null) c.setEmail(p.email);
        if (p.vito_person_ref != null) c.setVitoPersonRef(p.vito_person_ref);
        if (p.varapi_provider_ref != null) c.setVarapiProviderRef(p.varapi_provider_ref);
        if (p.tuso_facility_ref != null) c.setTusoFacilityRef(p.tuso_facility_ref);
        if (p.indawo_site_ref != null) c.setIndawoSiteRef(p.indawo_site_ref);
        if (p.organisation_ref != null) c.setOrganisationRef(p.organisation_ref);
        if (p.programme_ref != null) c.setProgrammeRef(p.programme_ref);
        if (p.current_status != null) c.setCurrentStatus(p.current_status);
        if (p.licence_ref != null) c.setLicenceRef(p.licence_ref);
        if (p.training_status != null) c.setTrainingStatus(p.training_status);
        if (p.risk_level != null) c.setRiskLevel(p.risk_level);
        if (p.trust_verified != null) c.setTrustVerified(p.trust_verified);
        if (p.council_verified != null) c.setCouncilVerified(p.council_verified);
        if (p.offline_access_allowed != null) c.setOfflineAccessAllowed(p.offline_access_allowed);
        if (p.active != null) c.setActive(p.active);
    }

    private Map<String, Object> toCourier(DriverCourierProfileEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courier_id", c.getCourierId());
        m.put("courier_code", c.getCourierCode());
        m.put("display_name", c.getDisplayName());
        m.put("courier_kind", c.getCourierKind());
        m.put("phone", c.getPhone());
        m.put("email", c.getEmail());
        m.put("vito_person_ref", c.getVitoPersonRef());
        m.put("varapi_provider_ref", c.getVarapiProviderRef());
        m.put("tuso_facility_ref", c.getTusoFacilityRef());
        m.put("indawo_site_ref", c.getIndawoSiteRef());
        m.put("organisation_ref", c.getOrganisationRef());
        m.put("programme_ref", c.getProgrammeRef());
        m.put("current_status", c.getCurrentStatus());
        m.put("training_status", c.getTrainingStatus());
        m.put("licence_ref", c.getLicenceRef());
        m.put("risk_level", c.getRiskLevel());
        m.put("trust_verified", c.isTrustVerified());
        m.put("council_verified", c.isCouncilVerified());
        m.put("offline_access_allowed", c.isOfflineAccessAllowed());
        m.put("active", c.isActive());
        m.put("is_demo", c.isDemo());
        return m;
    }

    public static class CourierPayload {
        public String courier_code;
        public String display_name;
        public String courier_kind;
        public String phone;
        public String email;
        public String vito_person_ref;
        public String varapi_provider_ref;
        public String tuso_facility_ref;
        public String indawo_site_ref;
        public String organisation_ref;
        public String programme_ref;
        public String current_status;
        public String licence_ref;
        public String training_status;
        public String risk_level;
        public Boolean trust_verified;
        public Boolean council_verified;
        public Boolean offline_access_allowed;
        public Boolean active;
    }
}
