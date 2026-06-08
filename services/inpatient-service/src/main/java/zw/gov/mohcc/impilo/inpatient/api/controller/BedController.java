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
