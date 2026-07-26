package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.core.BedManagementService;
import zw.gov.mohcc.impilo.inpatient.core.BedNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/beds")
public class BedController {

    private final BedManagementService bedManagementService;

    public BedController(BedManagementService bedManagementService) {
        this.bedManagementService = bedManagementService;
    }

    @GetMapping("/wards")
    public ResponseEntity<Map<String, Object>> listWards(
            @RequestParam(name = "facility_id") UUID facilityId) {
        List<Map<String, Object>> data = bedManagementService.listWardResources(facilityId);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listBeds(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(name = "ward_id", required = false) UUID wardId,
            @RequestParam(required = false) String status) {
        List<Map<String, Object>> data = bedManagementService.listBedResources(facilityId, wardId, status);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String status = stringVal(body, "status");
        if (status == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "STATUS_REQUIRED", "message", "status is required")));
        }
        try {
            Map<String, Object> resource = bedManagementService.updateBedStatus(id, status);
            return ResponseEntity.ok(Map.of("data", resource));
        } catch (BedNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "BED_NOT_FOUND", "message", ex.getMessage())));
        }
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignPatient(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> resource = bedManagementService.assignPatient(id, body);
            return ResponseEntity.ok(Map.of("data", resource));
        } catch (BedNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "BED_NOT_FOUND", "message", ex.getMessage())));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "ASSIGN_FAILED", "message", ex.getMessage())));
        }
    }

    @PostMapping("/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeBed(@PathVariable UUID id) {
        try {
            Map<String, Object> resource = bedManagementService.dischargeBed(id);
            return ResponseEntity.ok(Map.of("data", resource));
        } catch (BedNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "BED_NOT_FOUND", "message", ex.getMessage())));
        }
    }

    /**
     * Provision a ward and materialise its beds. Sits beside {@code GET /wards} because this is
     * where wards are read from — the BFF's create call has until now gone to tuso, which serves
     * no such path.
     *
     * <p>Capabilities (isolation/oxygen/monitoring/ICU) are absent unless declared: they gate
     * patient placement through {@code BedSafetyEvaluator}, so an undeclared capability must not
     * be assumed present.</p>
     */
    @PostMapping("/wards")
    public ResponseEntity<Map<String, Object>> createWard(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestBody Map<String, Object> body) {
        String facilityId = stringVal(body, "facilityId", "facility_id");
        String name = stringVal(body, "name");
        if (facilityId == null || name == null) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "WARD_INVALID",
                    "message", "facilityId and name are required")));
        }

        Object bedsValue = body.get("totalBeds") != null ? body.get("totalBeds") : body.get("total_beds");
        int totalBeds;
        try {
            totalBeds = bedsValue == null ? 0 : Integer.parseInt(String.valueOf(bedsValue).trim());
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "WARD_INVALID", "message", "totalBeds must be a whole number")));
        }

        BedManagementService.CreateWardCommand command = new BedManagementService.CreateWardCommand(
                UUID.fromString(facilityId),
                name,
                stringVal(body, "wardType", "ward_type"),
                stringVal(body, "floorLabel", "floor_label"),
                totalBeds,
                stringVal(body, "genderDesignation", "gender_designation"),
                stringVal(body, "ageGroup", "age_group"),
                boolVal(body, "isolationCapable", "isolation_capable"),
                boolVal(body, "oxygenAvailable", "oxygen_available"),
                boolVal(body, "monitoringCapable", "monitoring_capable"),
                boolVal(body, "icuCapable", "icu_capable"));

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", bedManagementService.createWard(tenantId, command)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "WARD_INVALID", "message", ex.getMessage())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", Map.of(
                    "code", "WARD_DUPLICATE", "message", ex.getMessage())));
        }
    }

    /** Tri-state: null means "not stated", which the service treats as capability absent. */
    private static Boolean boolVal(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value instanceof Boolean b) {
                return b;
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                String v = String.valueOf(value).trim();
                if ("true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v)) return Boolean.TRUE;
                if ("false".equalsIgnoreCase(v) || "no".equalsIgnoreCase(v) || "0".equals(v)) return Boolean.FALSE;
            }
        }
        return null;
    }

    private static String stringVal(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
