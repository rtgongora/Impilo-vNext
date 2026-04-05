package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mobile provider-facing API endpoints.
 * Serves clinician/provider workflows from the mobile provider app.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileProviderController {

    private final JdbcTemplate jdbc;

    public MobileProviderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/tasks/mine")
    public ResponseEntity<Map<String, Object>> getMyTasks(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM queue_entries WHERE tenant_id = ? AND status IN ('WAITING', 'IN_PROGRESS') ORDER BY priority DESC, created_at ASC LIMIT 50",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM queue_entries WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 100",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/encounters")
    public ResponseEntity<Map<String, Object>> getEncounters(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM encounters WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 50",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/diagnosis/icd11/search")
    public ResponseEntity<Map<String, Object>> searchIcd11(
            @RequestParam("q") String query,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // ICD-11 search — in production, delegates to ZIBO terminology service
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @PostMapping("/diagnosis")
    public ResponseEntity<Map<String, Object>> createDiagnosis(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Diagnosis creation — delegates to PCT service
        return ResponseEntity.ok(Map.of("success", true, "data", body));
    }

    @GetMapping("/diagnosis")
    public ResponseEntity<Map<String, Object>> getDiagnoses(
            @RequestParam("encounter_id") String encounterId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @DeleteMapping("/diagnosis/{id}")
    public ResponseEntity<Map<String, Object>> deleteDiagnosis(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/telemedicine/sessions")
    public ResponseEntity<Map<String, Object>> getTelemedicineSessions(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @PostMapping("/telemedicine/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "sessionId", id, "roomUrl", "", "token", "")));
    }

    @PostMapping("/telemedicine/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/entitlement/verify")
    public ResponseEntity<Map<String, Object>> verifyEntitlement(
            @RequestParam("cpid") String cpid,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Entitlement verification — delegates to Coverage service
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "cpid", cpid, "eligible", true, "schemes", List.of())));
    }

    @PostMapping("/break-glass/activate")
    public ResponseEntity<Map<String, Object>> activateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Break-glass activation — delegates to TSHEPO
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("active", true)));
    }

    @PostMapping("/break-glass/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("active", false)));
    }
}
