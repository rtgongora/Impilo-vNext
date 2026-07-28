package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.EmergencyMedicationAdminService;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyMedicationAdminEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ED / non-admitted medication administration (pct V207, W8b). */
@RestController
@RequestMapping("/v1/emergency")
public class EmergencyMedicationAdminController {

    private final EmergencyMedicationAdminService adminService;

    public EmergencyMedicationAdminController(EmergencyMedicationAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/episodes/{episodeId}/medications")
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@PathVariable UUID episodeId,
                                                                    @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var admin = adminService.record(episodeId, ctx.tenantId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(row(admin), ctx.correlationId().toString()));
    }

    @GetMapping("/episodes/{episodeId}/medications")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forEpisode(@PathVariable UUID episodeId) {
        TrustContext ctx = TrustContextHolder.require();
        var admins = adminService.forEpisode(episodeId, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(admins.stream().map(this::row).toList(), ctx.correlationId().toString()));
    }

    private Map<String, Object> row(EmergencyMedicationAdminEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("admin_id", a.getAdminId().toString());
        m.put("episode_id", a.getEpisodeId().toString());
        m.put("drug_name", a.getDrugName());
        m.put("drug_code", a.getDrugCode());
        m.put("dose_value", a.getDoseValue());
        m.put("dose_unit", a.getDoseUnit());
        m.put("route", a.getRoute());
        m.put("high_risk", a.isHighRisk());
        m.put("administered_at", a.getAdministeredAt() != null ? a.getAdministeredAt().toString() : null);
        m.put("administered_by", a.getAdministeredBy());
        m.put("authorised_by", a.getAuthorisedBy());
        m.put("witnessed_by", a.getWitnessedBy());
        m.put("superseded_by", a.getSupersededBy() != null ? a.getSupersededBy().toString() : null);
        m.put("correction_reason", a.getCorrectionReason());
        return m;
    }
}
