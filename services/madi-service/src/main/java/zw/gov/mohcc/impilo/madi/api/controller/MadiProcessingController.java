package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.ProcessingService;
import zw.gov.mohcc.impilo.madi.domain.ComponentType;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodComponentEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodProcessingBatchEntity;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/processing")
public class MadiProcessingController {

    private final ProcessingService processingService;

    public MadiProcessingController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping("/receive")
    public ResponseEntity<BloodProcessingBatchEntity> receive(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processingService.receive(
                tenantId, UUID.fromString(required(body, "unit_id")), facilityId));
    }

    @PostMapping("/{batchId}/test")
    public BloodProcessingBatchEntity test(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID batchId,
            @RequestBody Map<String, Object> body) {
        return processingService.recordTest(tenantId, batchId,
                required(body, "test_results_json"), facilityId);
    }

    @PostMapping("/{batchId}/quarantine")
    public BloodProcessingBatchEntity quarantine(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID batchId,
            @RequestBody Map<String, Object> body) {
        return processingService.quarantine(tenantId, batchId, required(body, "reason"), facilityId);
    }

    @PostMapping("/{batchId}/release")
    public BloodProcessingBatchEntity release(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID batchId,
            @RequestBody Map<String, Object> body) {
        return processingService.release(tenantId, batchId, str(body, "released_by"), facilityId);
    }

    @PostMapping("/components")
    public ResponseEntity<BloodComponentEntity> prepareComponent(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        Integer volume = body.get("volume_ml") != null ? Integer.parseInt(body.get("volume_ml").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(processingService.prepareComponent(
                tenantId, UUID.fromString(required(body, "unit_id")),
                ComponentType.valueOf(required(body, "component_type")),
                volume, uuid(body, "product_id"), facilityId));
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null && !v.toString().isBlank() ? UUID.fromString(v.toString()) : null;
    }
}
