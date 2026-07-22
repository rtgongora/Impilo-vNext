package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.Map;

/**
 * The council officer's regulatory console (ROM-U2). Proxies the regulator lanes for processing
 * professional applications — the queue, an application's full correspondence (incl. internal
 * notes), opening/closing RFIs, internal + applicant-visible messages, and recording decisions.
 * Recusal (a person cannot process their own application) is enforced downstream in varapi
 * (RegulatoryApplicationCorrespondenceService → 403 RECUSAL_REQUIRED), surfaced verbatim here.
 */
@RestController
@RequestMapping("/internal/v1/regulatory/console")
public class RegulatoryConsoleBffController {

    private final VarapiServiceClient varapiClient;
    private final TusoServiceClient tusoClient;
    private final ReportingServiceClient reportingClient;

    public RegulatoryConsoleBffController(VarapiServiceClient varapiClient,
                                          TusoServiceClient tusoClient,
                                          ReportingServiceClient reportingClient) {
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.reportingClient = reportingClient;
    }

    @GetMapping("/applications")
    public ResponseEntity<JsonNode> queue() {
        return ResponseEntity.ok(varapiClient.getOpenApplications());
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<JsonNode> application(@PathVariable Long applicationId) {
        return ResponseEntity.ok(varapiClient.getApplication(applicationId));
    }

    /** The regulator's full correspondence view — includes INTERNAL notes. */
    @GetMapping("/applications/{applicationId}/correspondence")
    public ResponseEntity<JsonNode> correspondence(@PathVariable Long applicationId) {
        return ResponseEntity.ok(varapiClient.getRegulatoryApplication(applicationId, "/correspondence/regulator"));
    }

    /** Open an RFI (recusal-guarded downstream). */
    @PostMapping("/applications/{applicationId}/rfis")
    public ResponseEntity<JsonNode> openRfi(@PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                varapiClient.postRegulatoryApplication(applicationId, "/correspondence/rfis", body));
    }

    @PostMapping("/applications/{applicationId}/rfis/{rfiId}/close")
    public ResponseEntity<JsonNode> closeRfi(@PathVariable Long applicationId, @PathVariable Long rfiId) {
        return ResponseEntity.ok(
                varapiClient.postRegulatoryApplication(applicationId, "/correspondence/rfis/" + rfiId + "/close", Map.of()));
    }

    /** Post a message — visibility APPLICANT (to the applicant) or INTERNAL (internal note). */
    @PostMapping("/applications/{applicationId}/messages")
    public ResponseEntity<JsonNode> message(@PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                varapiClient.postRegulatoryApplication(applicationId, "/correspondence/regulator/messages", body));
    }

    /** Record the decision on an application (recusal-guarded downstream). */
    @PostMapping("/applications/{applicationId}/decision")
    public ResponseEntity<JsonNode> decision(@PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(varapiClient.postApplicationAction(applicationId, "decision", body));
    }

    // ── Disciplinary proceeding lanes (ROM-U3) — all recusal-guarded downstream ──
    @GetMapping("/disciplinary/provider/{providerId}")
    public ResponseEntity<JsonNode> disciplinaryForProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(varapiClient.getDisciplinaryForProvider(providerId));
    }

    @PostMapping("/disciplinary")
    public ResponseEntity<JsonNode> openProceeding(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postDisciplinary("", body));
    }

    @PostMapping("/disciplinary/{caseId}/advance")
    public ResponseEntity<JsonNode> advanceProceeding(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(varapiClient.postDisciplinary("/" + caseId + "/advance", body));
    }

    @PostMapping("/disciplinary/{caseId}/determine")
    public ResponseEntity<JsonNode> determineProceeding(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(varapiClient.postDisciplinary("/" + caseId + "/determine", body));
    }

    // ── Committee hearings / dockets (ROM-U4) ──
    @GetMapping("/hearings/my-docket")
    public ResponseEntity<JsonNode> myDocket() {
        return ResponseEntity.ok(varapiClient.getMyDocket());
    }

    @PostMapping("/hearings/docket")
    public ResponseEntity<JsonNode> assignDocket(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postHearing("/docket", body));
    }

    @PostMapping("/hearings/{caseId}/schedule")
    public ResponseEntity<JsonNode> scheduleHearing(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postHearing("/" + caseId + "/schedule", body));
    }

    @PostMapping("/hearings/{caseId}/appeal")
    public ResponseEntity<JsonNode> fileAppeal(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postHearing("/" + caseId + "/appeal", body));
    }

    // ── Dashboards + HPA oversight (ROM-U5) ──
    /** Run a governed regulatory report definition by key (W9 defs: reg.operational.*, reg.management.*, …). */
    @PostMapping("/reports/{reportKey}/run")
    public ResponseEntity<JsonNode> runReport(@PathVariable String reportKey, @RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(reportingClient.runReport(reportKey, params));
    }

    @GetMapping("/oversight/grants")
    public ResponseEntity<JsonNode> oversightGrants(@RequestParam("orgId") String orgId) {
        return ResponseEntity.ok(varapiClient.getOversightGrants(orgId));
    }

    @PostMapping("/oversight/grants")
    public ResponseEntity<JsonNode> grantOversight(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postOversightGrant(body));
    }

    // ── Practice establishment — regulator decision lanes (ROM-U6) ──
    @PostMapping("/practice-establishments/{id}/approve")
    public ResponseEntity<JsonNode> approveEstablishment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(tusoClient.postEstablishment("/" + id + "/approve", body));
    }

    @PostMapping("/practice-establishments/{id}/reject")
    public ResponseEntity<JsonNode> rejectEstablishment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(tusoClient.postEstablishment("/" + id + "/reject", body));
    }

    // ── Bulk import: STAGE → MATCH → REVIEW → APPLY (ROM-U7) ──
    @GetMapping("/imports")
    public ResponseEntity<JsonNode> imports() {
        return ResponseEntity.ok(varapiClient.getImports());
    }

    @PostMapping("/imports")
    public ResponseEntity<JsonNode> stageImport(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postImport("", body));
    }

    @GetMapping("/imports/{batchId}/rows")
    public ResponseEntity<JsonNode> importRows(@PathVariable Long batchId) {
        return ResponseEntity.ok(varapiClient.getImportRows(batchId));
    }

    @PostMapping("/imports/rows/{rowId}/review")
    public ResponseEntity<JsonNode> reviewImportRow(@PathVariable Long rowId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(varapiClient.postImport("/rows/" + rowId + "/review", body));
    }

    @PostMapping("/imports/{batchId}/apply")
    public ResponseEntity<JsonNode> applyImport(@PathVariable Long batchId) {
        return ResponseEntity.ok(varapiClient.postImport("/" + batchId + "/apply", Map.of()));
    }
}
