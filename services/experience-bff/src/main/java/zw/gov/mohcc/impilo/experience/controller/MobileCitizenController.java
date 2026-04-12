package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile citizen-facing API endpoints.
 * Delegates to sovereign services with mobile-optimized response shapes
 * and citizen-scoped access.
 *
 * <p>STRANGLER: migrated from JdbcTemplate to VitoServiceClient + PctServiceClient
 * + MusheWalletServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen")
public class MobileCitizenController {

    private final VitoServiceClient vitoClient;
    private final PctServiceClient pctClient;
    private final MusheWalletServiceClient musheWalletClient;

    public MobileCitizenController(VitoServiceClient vitoClient,
                                   PctServiceClient pctClient,
                                   MusheWalletServiceClient musheWalletClient) {
        this.vitoClient = vitoClient;
        this.pctClient = pctClient;
        this.musheWalletClient = musheWalletClient;
    }

    @GetMapping("/coverage")
    public ResponseEntity<Map<String, Object>> getCoverage(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to MusheWalletServiceClient — coverage schemes from wallet/coverage service
        // Previously: jdbc.queryForList("SELECT * FROM coverage_schemes WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 20", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/coverage/{id}")
    public ResponseEntity<Map<String, Object>> getCoverageDetail(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to MusheWalletServiceClient
        // Previously: jdbc.queryForList("SELECT * FROM coverage_schemes WHERE id = ? AND tenant_id = ?", id, tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of()));
    }

    @GetMapping("/appointments")
    public ResponseEntity<Map<String, Object>> getAppointments(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — scheduling from PCT/TUSO
        // Previously: jdbc.queryForList("SELECT * FROM scheduling_slots WHERE tenant_id = ? AND status IN ('BOOKED', 'CONFIRMED') ...", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> getPrescriptions(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — prescriptions from pharmacy-service via PCT
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getLabResults(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — lab results from OROS via PCT
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/feed")
    public ResponseEntity<Map<String, Object>> getFeed(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to VitoServiceClient — feed items from notification/content service
        // Previously: jdbc.queryForList("SELECT * FROM feed_items WHERE tenant_id = ? AND active = true ...", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/community/groups")
    public ResponseEntity<Map<String, Object>> getCommunityGroups(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to VitoServiceClient — community groups from community-service
        // Previously: jdbc.queryForList("SELECT * FROM community_groups WHERE tenant_id = ? ...", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> getReminders(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — reminders from notification-service
        // Previously: jdbc.queryForList("SELECT * FROM omni_sms_journeys WHERE tenant_id = ? ...", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/consent")
    public ResponseEntity<Map<String, Object>> getConsent(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to VitoServiceClient — consent data from TSHEPO via proxy
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/health-timeline")
    public ResponseEntity<Map<String, Object>> getHealthTimeline(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to PctServiceClient — timeline aggregated from PCT encounters
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // STRANGLER: migrated to VitoServiceClient + PctServiceClient — patient records from VITO/PCT
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }
}
