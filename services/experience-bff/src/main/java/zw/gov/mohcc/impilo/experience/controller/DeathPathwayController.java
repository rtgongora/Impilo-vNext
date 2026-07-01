package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.UbomiServiceClient;

import java.util.Map;

/**
 * Experience-BFF facade for the WS#8 Death &amp; Post-Death Pathway. Composition / orchestration only —
 * it never owns death truth. Death-case + certification + mortuary calls proxy to pct-service (the
 * DeathCase owner); civil-registration calls proxy to ubomi-service (the CRVS owner). The inbound trust
 * context is forwarded by the RestTemplate interceptor. Backs the provider /work/clinical/death-cases,
 * /work/clinical/mortality-certification, /work/mortuary and /work/crvs/death-registration surfaces.
 * No payment is ever composed into this flow — death documentation is never payment-gated.
 */
@RestController
@RequestMapping("/internal/v1/death")
public class DeathPathwayController {

    private final PctServiceClient pct;
    private final UbomiServiceClient ubomi;

    public DeathPathwayController(PctServiceClient pct, UbomiServiceClient ubomi) {
        this.pct = pct;
        this.ubomi = ubomi;
    }

    // ── Death cases (PCT) ──
    @PostMapping("/confirm")
    public ResponseEntity<JsonNode> confirm(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.confirmDeath(body));
    }

    /** Body brought in dead to the facility (facility receipt + mortuary + medico-legal apply). */
    @PostMapping("/brought-in-dead")
    public ResponseEntity<JsonNode> broughtInDead(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.confirmBroughtInDead(body));
    }

    /** Unverified community / field death report (CHW / family / surveillance) — not a confirmation. */
    @PostMapping("/community-report")
    public ResponseEntity<JsonNode> communityReport(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.reportCommunityDeath(body));
    }

    /** Verbal-autopsy interview for a community/field death (WHO VA — probable cause, never certified). */
    @PostMapping("/cases/{caseId}/verbal-autopsy")
    public ResponseEntity<JsonNode> verbalAutopsy(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.recordDeathVerbalAutopsy(caseId, body));
    }

    @GetMapping("/cases/{caseId}/verbal-autopsy")
    public ResponseEntity<JsonNode> listVerbalAutopsies(@PathVariable String caseId) {
        return ResponseEntity.ok(pct.listDeathVerbalAutopsies(caseId));
    }

    /** Non-facility (field) body management — safe burial / release / handover (no mortuary custody). */
    @PostMapping("/cases/{caseId}/field-body-management")
    public ResponseEntity<JsonNode> fieldBodyManagement(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.recordDeathFieldBodyManagement(caseId, body));
    }

    @GetMapping("/cases/{caseId}/field-body-management")
    public ResponseEntity<JsonNode> listFieldBodyManagement(@PathVariable String caseId) {
        return ResponseEntity.ok(pct.listDeathFieldBodyManagement(caseId));
    }

    @GetMapping("/cases")
    public ResponseEntity<JsonNode> listCases() {
        return ResponseEntity.ok(pct.listDeathCases());
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<JsonNode> getCase(@PathVariable String caseId) {
        return ResponseEntity.ok(pct.getDeathCase(caseId));
    }

    @GetMapping("/cases/{caseId}/audit")
    public ResponseEntity<JsonNode> audit(@PathVariable String caseId) {
        return ResponseEntity.ok(pct.getDeathAudit(caseId));
    }

    @PostMapping("/cases/{caseId}/public-health-screen")
    public ResponseEntity<JsonNode> publicHealthScreen(@PathVariable String caseId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(pct.screenDeathPublicHealth(caseId, body != null ? body : Map.of()));
    }

    @PostMapping("/cases/{caseId}/coroner-status")
    public ResponseEntity<JsonNode> coronerStatus(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(pct.updateDeathCoronerStatus(caseId, body));
    }

    // ── Cause-of-death certification (PCT) ──
    @PostMapping("/cases/{caseId}/certify")
    public ResponseEntity<JsonNode> certify(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(pct.certifyDeathCause(caseId, body));
    }

    // ── Mortuary / body custody (PCT) ──
    @GetMapping("/cases/{caseId}/body")
    public ResponseEntity<JsonNode> getBody(@PathVariable String caseId) {
        return ResponseEntity.ok(pct.getDeathBodyCustody(caseId));
    }

    @PostMapping("/cases/{caseId}/body/receive")
    public ResponseEntity<JsonNode> receiveBody(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.receiveDeathBody(caseId, body));
    }

    @PostMapping("/cases/{caseId}/body/postmortem-hold")
    public ResponseEntity<JsonNode> postmortemHold(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(pct.setDeathPostmortemHold(caseId, body));
    }

    @PostMapping("/cases/{caseId}/body/release")
    public ResponseEntity<JsonNode> releaseBody(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(pct.releaseDeathBody(caseId, body));
    }

    // ── Supporting documents (PCT records the reference; the file lives in the document service) ──
    @PostMapping("/cases/{caseId}/documents")
    public ResponseEntity<JsonNode> attachDocument(@PathVariable String caseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pct.attachDeathDocument(caseId, body));
    }

    // ── CRVS (Ubomi owns) ──
    @PostMapping("/cases/{caseId}/crvs-package")
    public ResponseEntity<JsonNode> stageCrvs(@PathVariable String caseId) {
        return ResponseEntity.ok(pct.stageDeathCrvsPackage(caseId));
    }

    @GetMapping("/crvs/notifications")
    public ResponseEntity<JsonNode> listCrvsNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ubomi.listDeathNotifications(page, size));
    }

    @PostMapping("/crvs/notifications/{notificationId}/package-ready")
    public ResponseEntity<JsonNode> crvsPackageReady(@PathVariable long notificationId) {
        return ResponseEntity.ok(ubomi.markDeathPackageReady(notificationId));
    }

    @PostMapping("/crvs/notifications/{notificationId}/register")
    public ResponseEntity<JsonNode> crvsRegister(@PathVariable long notificationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ubomi.registerDeath(notificationId, body));
    }
}
