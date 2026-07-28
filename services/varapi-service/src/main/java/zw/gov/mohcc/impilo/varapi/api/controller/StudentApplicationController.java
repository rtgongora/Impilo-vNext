package zw.gov.mohcc.impilo.varapi.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.core.CouncilFeeGate;
import zw.gov.mohcc.impilo.varapi.core.StudentAdmissionService;
import zw.gov.mohcc.impilo.varapi.core.StudentApplicationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationSectionEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The three sides of a student registration application (NCZ-W1C).
 *
 * <p>The routes are deliberately split by WHO is acting rather than by what is being touched, and
 * the split is the security boundary. {@code /contributor/**} takes an invitation id and never an
 * application id: a training institution cannot address the application directly, so there is no
 * endpoint on which forgetting a scope check would expose the whole case. The applicant and
 * regulator lanes address the application; the contributor lane cannot.</p>
 */
@RestController
@RequestMapping("/v1/internal/student-applications")
public class StudentApplicationController {

    private final StudentApplicationService applicationService;
    private final StudentAdmissionService admissionService;
    private final CouncilFeeGate feeGate;

    public StudentApplicationController(StudentApplicationService applicationService,
                                        StudentAdmissionService admissionService,
                                        CouncilFeeGate feeGate) {
        this.applicationService = applicationService;
        this.admissionService = admissionService;
        this.feeGate = feeGate;
    }

    // ── The applicant ────────────────────────────────────────────────────────────────────────

    @GetMapping("/{applicationId}/sections")
    public List<Map<String, Object>> sections(@RequestHeader("X-Tenant-ID") UUID tenantId,
                                              @PathVariable Long applicationId) {
        return applicationService.sectionsForApplicant(tenantId, applicationId).stream()
                .map(StudentApplicationController::section)
                .toList();
    }

    @PostMapping("/{applicationId}/sections/{sectionKey}/resubmit")
    public Map<String, Object> resubmit(@RequestHeader("X-Tenant-ID") UUID tenantId,
                                        @PathVariable Long applicationId,
                                        @PathVariable String sectionKey,
                                        @RequestBody(required = false) JsonNode content) {
        return section(applicationService.resubmitSection(
                tenantId, applicationId, sectionKey, content));
    }

    // ── The contributor: addressed by INVITATION, never by application ───────────────────────

    @GetMapping("/contributor/{inviteId}/sections")
    public List<Map<String, Object>> contributorSections(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable Long inviteId) {
        return applicationService.sectionsForContributor(tenantId, inviteId).stream()
                .map(StudentApplicationController::section)
                .toList();
    }

    @PostMapping("/contributor/{inviteId}/sections/{sectionKey}")
    public Map<String, Object> contributorCompletes(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable Long inviteId,
            @PathVariable String sectionKey,
            @RequestBody(required = false) JsonNode content) {
        return section(applicationService.contributorCompletesSection(
                tenantId, inviteId, sectionKey, content));
    }

    // ── The regulator ────────────────────────────────────────────────────────────────────────

    @PostMapping("/{applicationId}/sections/{sectionKey}/return")
    public Map<String, Object> returnSection(@RequestHeader("X-Tenant-ID") UUID tenantId,
                                             @RequestHeader(value = "X-Actor-ID", required = false) String actor,
                                             @PathVariable Long applicationId,
                                             @PathVariable String sectionKey,
                                             @RequestBody Map<String, String> body) {
        return section(applicationService.returnSection(
                tenantId, applicationId, sectionKey, body.get("reason"), actor));
    }

    /**
     * What the fee gate says, before anyone tries to admit. Exposed so the regulator's screen can
     * show "not configured, awaiting NCZ-DEC-002" instead of discovering it at the decision.
     */
    @GetMapping("/fee-verdict")
    public Map<String, Object> feeVerdict(@RequestHeader("X-Tenant-ID") UUID tenantId,
                                          @RequestParam Long councilId,
                                          @RequestParam String feeCode) {
        CouncilFeeGate.FeeVerdict verdict = feeGate.verdictFor(tenantId, councilId, feeCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verdict", verdict.verdict().name());
        body.put("amount", verdict.amount());
        body.put("currency", verdict.currency());
        body.put("decisionRef", verdict.decisionRef());
        body.put("chargeable", verdict.allowsChargeToProceed());
        body.put("explanation", verdict.explain());
        return body;
    }

    @PostMapping("/{applicationId}/admit")
    public ResponseEntity<Map<String, Object>> admit(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actor,
            @PathVariable Long applicationId,
            @RequestBody Map<String, Object> body) {

        // No defaults. These named NCZ's register, policy and fee, which is fine until a second
        // council calls the same endpoint — and then a caller who omits one is admitting someone
        // against ANOTHER council's configuration. It fails closed today (the lookup is scoped by
        // council, so it 409s), but a confusing refusal is not the same as a clear requirement, and
        // the next default might resolve. A council says which register it is admitting to.
        StudentAdmissionService.Admission admission = admissionService.admit(
                tenantId,
                Long.valueOf(String.valueOf(body.get("councilId"))),
                applicationId,
                Long.valueOf(String.valueOf(body.get("providerId"))),
                required(body, "registerCode"),
                required(body, "policyKey"),
                required(body, "feeCode"),
                actor);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("indexNumber", admission.indexNumber());
        response.put("registerEntryId", admission.registerEntryId());
        response.put("registerCode", admission.registerCode());
        response.put("effectiveFrom", admission.effectiveFrom());
        return ResponseEntity.ok(response);
    }

    private static String required(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "'" + field + "' is required. Admission is council-specific and must not fall "
                            + "back to another council's configuration.");
        }
        return String.valueOf(value);
    }

    /**
     * The section as anyone may see it. Note what is NOT here: no internal assessment, no risk
     * flag, no reviewer note. Those live on the case-message rail with its own visibility model,
     * and the surest way to keep them off an applicant's screen is for them never to enter a
     * response an applicant can reach.
     */
    private static Map<String, Object> section(ApplicationSectionEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sectionKey", entity.getSectionKey());
        row.put("label", entity.getLabel());
        row.put("sequenceNo", entity.getSequenceNo());
        row.put("state", entity.getState());
        row.put("content", entity.getContent());
        row.put("completedAt", entity.getCompletedAt());
        row.put("returnedReason", entity.getReturnedReason());
        row.put("returnedAt", entity.getReturnedAt());
        row.put("returnCount", entity.getReturnCount());
        return row;
    }
}
