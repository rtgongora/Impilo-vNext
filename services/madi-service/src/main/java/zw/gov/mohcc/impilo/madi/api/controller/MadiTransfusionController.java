package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.TransfusionService;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionEpisodeEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionObservationEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/transfusions")
public class MadiTransfusionController {

    private final TransfusionService transfusionService;

    public MadiTransfusionController(TransfusionService transfusionService) {
        this.transfusionService = transfusionService;
    }

    @GetMapping
    public List<TransfusionEpisodeEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "patient_cpid", required = false) String patientCpid) {
        return transfusionService.listEpisodes(tenantId, facilityId, status, patientCpid);
    }

    @PostMapping
    public ResponseEntity<TransfusionEpisodeEntity> start(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transfusionService.startEpisode(
                tenantId, uuid(body, "order_id"), uuid(body, "issue_id"),
                required(body, "patient_cpid"), str(body, "started_by"),
                facilityId, uuid(body, "ward_id")));
    }

    @PostMapping("/{episodeId}/observations")
    public ResponseEntity<TransfusionObservationEntity> observation(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        BigDecimal value = body.get("value_numeric") != null
                ? new BigDecimal(body.get("value_numeric").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(transfusionService.recordObservation(
                tenantId, episodeId, required(body, "observation_type"),
                value, str(body, "value_text"), str(body, "unit"),
                str(body, "observed_by"), facilityId));
    }

    @PostMapping("/{episodeId}/complete")
    public TransfusionEpisodeEntity complete(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        return transfusionService.complete(tenantId, episodeId,
                required(body, "outcome_status"), str(body, "outcome_notes"),
                str(body, "recorded_by"), facilityId);
    }

    @PostMapping("/{episodeId}/verify")
    public TransfusionEpisodeEntity verify(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        return transfusionService.verify(tenantId, episodeId, str(body, "verified_by"));
    }

    @PostMapping("/{episodeId}/pre-verify")
    public TransfusionEpisodeEntity preVerify(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        return transfusionService.verifyPreTransfusion(
                tenantId, episodeId,
                required(body, "patient_cpid"),
                UUID.fromString(required(body, "blood_unit_id")),
                required(body, "patient_method"),
                str(body, "patient_biometric_ref"),
                required(body, "unit_method"),
                str(body, "unit_scan_ref"),
                str(body, "verified_by"));
    }

    @GetMapping("/{episodeId}")
    public ResponseEntity<TransfusionEpisodeEntity> getEpisode(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID episodeId) {
        return transfusionService.getEpisode(tenantId, episodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
