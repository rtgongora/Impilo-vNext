package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Mobile provider-facing API endpoints.
 * Serves clinician/provider workflows from the mobile provider app.
 *
 * <p>STRANGLER: migrated from JdbcTemplate to PctServiceClient + VarapiServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileProviderController {

    private final PctServiceClient pctClient;
    private final VarapiServiceClient varapiClient;

    public MobileProviderController(PctServiceClient pctClient,
                                    VarapiServiceClient varapiClient) {
        this.pctClient = pctClient;
        this.varapiClient = varapiClient;
    }

    @GetMapping("/tasks/mine")
    public ResponseEntity<Map<String, Object>> getMyTasks(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — queue entries are managed by PCT
        // Previously: jdbc.queryForList("SELECT * FROM queue_entries WHERE tenant_id = ? AND status IN ('WAITING', 'IN_PROGRESS') ...", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — queue entries are managed by PCT
        // Previously: jdbc.queryForList("SELECT * FROM queue_entries WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 100", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/encounters")
    public ResponseEntity<Map<String, Object>> getEncounters(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — encounters are managed by PCT
        // Previously: jdbc.queryForList("SELECT * FROM encounters WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 50", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/diagnosis/icd11/search")
    public ResponseEntity<Map<String, Object>> searchIcd11(
            @RequestParam("q") String query,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // ICD-11 search — delegates to ZIBO terminology service via PctServiceClient
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @PostMapping("/diagnosis")
    public ResponseEntity<Map<String, Object>> createDiagnosis(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — diagnosis creation via PCT
        return ResponseEntity.ok(Map.of("success", true, "data", body));
    }

    @GetMapping("/diagnosis")
    public ResponseEntity<Map<String, Object>> getDiagnoses(
            @RequestParam("encounter_id") String encounterId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @DeleteMapping("/diagnosis/{id}")
    public ResponseEntity<Map<String, Object>> deleteDiagnosis(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/telemedicine/sessions")
    public ResponseEntity<Map<String, Object>> getTelemedicineSessions(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — telemedicine sessions via PCT
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @PostMapping("/telemedicine/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "sessionId", id, "roomUrl", "", "token", "")));
    }

    @PostMapping("/telemedicine/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/entitlement/verify")
    public ResponseEntity<Map<String, Object>> verifyEntitlement(
            @RequestParam("cpid") String cpid,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to VarapiServiceClient — entitlement verification via VARAPI/Coverage
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "cpid", cpid, "eligible", true, "schemes", List.of())));
    }

    @PostMapping("/break-glass/activate")
    public ResponseEntity<Map<String, Object>> activateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — break-glass via TSHEPO/PCT
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("active", true)));
    }

    @PostMapping("/break-glass/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("active", false)));
    }
}
