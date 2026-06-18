package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.VitalsService;
import zw.gov.mohcc.impilo.pct.persistence.entity.VitalsEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Patient vitals — clinician- and device-recorded observations.
 * Consumed by the Experience BFF {@code /internal/v1/vitals}.
 */
@RestController
@RequestMapping("/v1/vitals")
public class VitalsController {

    private final VitalsService vitalsService;

    public VitalsController(VitalsService vitalsService) {
        this.vitalsService = vitalsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "encounter_id", required = false) String encounterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        Page<VitalsEntity> p;
        if (patientId != null && !patientId.isBlank()) {
            p = vitalsService.listByPatient(tenantId, patientId.trim(), page, size);
        } else if (encounterId != null && !encounterId.isBlank()) {
            p = vitalsService.listByEncounter(tenantId, encounterId.trim(), page, size);
        } else {
            return ResponseEntity.ok(ApiResponse.ok(
                    List.of(), TrustContextHolder.require().correlationId().toString()));
        }
        List<Map<String, Object>> content = p.getContent().stream()
                .map(vitalsService::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(
                content, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        VitalsEntity row = vitalsService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                vitalsService.toMap(row), TrustContextHolder.require().correlationId().toString()));
    }
}
