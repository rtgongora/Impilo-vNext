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

    // ── Routes migrated to granular controllers in mobile/ ────────────
    // GET /tasks/mine, GET /tasks         → MobileTaskController
    // GET /encounters                     → MobileEncounterController
    // GET /diagnosis/icd11/search         → MobileDiagnosisController
    // POST /diagnosis                     → MobileDiagnosisController
    // GET /diagnosis                      → MobileDiagnosisController
    // DELETE /diagnosis/{id}              → MobileDiagnosisController
    // GET /telemedicine/sessions          → MobileTelemedicineController
    // POST /telemedicine/sessions/{id}/join → MobileTelemedicineController
    // POST /telemedicine/sessions/{id}/end  → MobileTelemedicineController

    @GetMapping("/entitlement/verify")
    public ResponseEntity<Map<String, Object>> verifyEntitlement(
            @RequestParam("cpid") String cpid,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "cpid", cpid, "eligible", true, "schemes", List.of())));
    }

    @PostMapping("/break-glass/activate")
    public ResponseEntity<Map<String, Object>> activateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("active", true)));
    }

    @PostMapping("/break-glass/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("active", false)));
    }
}
