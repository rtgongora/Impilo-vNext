package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineOperatingModelRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only telemedicine operating-model doctrine for experience surfaces:
 * the governed virtual-hospital directory (service architecture, never
 * duplicate facility records) and the clinical-group taxonomy with its
 * fail-closed creation governance. Versioned code artifacts served straight
 * from the registry — no tenant data, same posture as
 * {@link SessionTemplateController}.
 */
@RestController
@RequestMapping("/internal/v1/telemedicine/operating-model")
public class TelemedicineOperatingModelController {

    private final TelemedicineOperatingModelRegistry registry;

    /**
     * Optional runtime composition (PCT queue stats + vashandi duty) for the
     * detail route. Optional wiring keeps the registry-only construction used
     * by existing tests valid; absent → payloads keep their honest
     * AWAITING_BACKEND defaults.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private zw.gov.mohcc.impilo.experience.telemedicine.VirtualHospitalCompositionService compositionService;

    public TelemedicineOperatingModelController(TelemedicineOperatingModelRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/virtual-hospitals")
    public ResponseEntity<Map<String, Object>> listVirtualHospitals(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        TelemedicineOperatingModelRegistry.Directory directory = registry.virtualHospitalDirectory();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("virtualHospitals", directory.virtualHospitals());
        attributes.put("source", directory.source());
        attributes.put("registryDegraded", directory.registryDegraded());
        return ok("virtual-hospital-directory", requestId, correlationId, attributes);
    }

    @GetMapping("/virtual-hospitals/{id}")
    public ResponseEntity<Map<String, Object>> getVirtualHospital(
            @PathVariable String id,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        TelemedicineOperatingModelRegistry.Directory directory = registry.virtualHospitalDirectory();
        Optional<Map<String, Object>> hospital = directory.byId(id);
        if (hospital.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of(
                            "code", "VIRTUAL_HOSPITAL_UNKNOWN",
                            "message", "No virtual hospital configured with id: " + id),
                    "meta", meta(requestId, correlationId)));
        }
        Map<String, Object> attributes = compositionService != null
                ? new LinkedHashMap<>(compositionService.compose(hospital.get()))
                : new LinkedHashMap<>(hospital.get());
        attributes.put("source", directory.source());
        attributes.put("registryDegraded", directory.registryDegraded());
        return ok("virtual-hospital", requestId, correlationId, attributes);
    }

    @GetMapping("/clinical-groups")
    public ResponseEntity<Map<String, Object>> clinicalGroups(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return ok("clinical-group-taxonomy", requestId, correlationId, registry.clinicalGroups());
    }

    private static ResponseEntity<Map<String, Object>> ok(
            String type, String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("type", type, "attributes", attributes));
        response.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of(
                "request_id", requestId != null ? requestId : UUID.randomUUID().toString(),
                "correlation_id", correlationId != null ? correlationId : UUID.randomUUID().toString());
    }
}
