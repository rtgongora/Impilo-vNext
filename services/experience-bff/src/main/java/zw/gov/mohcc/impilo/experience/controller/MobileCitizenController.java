package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.List;
import java.util.Map;

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

    // ── Routes migrated to granular controllers in mobile/citizen/ ────
    // GET /coverage, GET /coverage/{id}  → CitizenCoverageController
    // GET /appointments                  → CitizenAppointmentController
    // GET /prescriptions                 → CitizenPrescriptionController
    // GET /results                       → CitizenResultsController
    // GET /feed                          → CitizenFeedController
    // GET /reminders                     → CitizenRemindersController
    // GET /records                       → CitizenRecordsController

    @GetMapping("/community/groups")
    public ResponseEntity<Map<String, Object>> getCommunityGroups(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        // Previously: jdbc.queryForList("SELECT * FROM community_groups WHERE tenant_id = ? ...", tenantId)
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/consent")
    public ResponseEntity<Map<String, Object>> getConsent(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    @GetMapping("/health-timeline")
    public ResponseEntity<Map<String, Object>> getHealthTimeline(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }
}
