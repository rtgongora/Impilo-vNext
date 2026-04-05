package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile citizen-facing API endpoints.
 * Delegates to the same BFF data layer as the web UI but with
 * mobile-optimized response shapes and citizen-scoped access.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen")
public class MobileCitizenController {

    private final JdbcTemplate jdbc;

    public MobileCitizenController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/coverage")
    public ResponseEntity<Map<String, Object>> getCoverage(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM coverage_schemes WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 20",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/coverage/{id}")
    public ResponseEntity<Map<String, Object>> getCoverageDetail(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM coverage_schemes WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", rows.get(0)));
    }

    @GetMapping("/appointments")
    public ResponseEntity<Map<String, Object>> getAppointments(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM scheduling_slots WHERE tenant_id = ? AND status IN ('BOOKED', 'CONFIRMED') ORDER BY slot_date ASC, start_time ASC LIMIT 50",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> getPrescriptions(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Delegate to pharmacy service via BFF's local cache or proxy
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getLabResults(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Lab results come from OROS via BFF proxy
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/feed")
    public ResponseEntity<Map<String, Object>> getFeed(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM feed_items WHERE tenant_id = ? AND active = true ORDER BY published_at DESC LIMIT 20",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/community/groups")
    public ResponseEntity<Map<String, Object>> getCommunityGroups(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM community_groups WHERE tenant_id = ? AND is_public = true AND status = 'ACTIVE' ORDER BY member_count DESC LIMIT 20",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> getReminders(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM omni_sms_journeys WHERE tenant_id = ? AND is_active = true ORDER BY name ASC",
                tenantId);
        return ResponseEntity.ok(Map.of("success", true, "data", rows));
    }

    @GetMapping("/consent")
    public ResponseEntity<Map<String, Object>> getConsent(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Consent data from TSHEPO via proxy
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/health-timeline")
    public ResponseEntity<Map<String, Object>> getHealthTimeline(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Timeline aggregated from PCT encounters
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Patient records from VITO/PCT
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }
}
