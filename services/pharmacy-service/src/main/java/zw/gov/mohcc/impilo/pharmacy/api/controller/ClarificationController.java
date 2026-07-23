package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.core.ClarificationService;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ClarificationRequestEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OF-B15 — REST seam for the substitution → prescriber clarification loop
 * (§8.10.1: "any new substitution need → prescriber clarification loop,
 * never unilateral").
 *
 * <p>The pharmacist proposes; auto-allowed substitutions apply immediately,
 * approval-required substitutions create a PENDING clarification (the episode
 * holds). The prescriber resolves via approve/decline — decline is fail-closed.</p>
 */
@RestController
@RequestMapping("/v1")
public class ClarificationController {

    private static final Logger log = LoggerFactory.getLogger(ClarificationController.class);

    private final ClarificationService clarificationService;

    public ClarificationController(ClarificationService clarificationService) {
        this.clarificationService = clarificationService;
    }

    public record ProposeRequest(
            @NotBlank String targetDrugCode,
            @NotBlank String reason,
            boolean patientConsent) {}

    public record ResolveRequest(
            @NotBlank String decision,   // APPROVE | DECLINE
            String note) {}

    /** Pharmacist proposes a substitution for a dispense item. */
    @PostMapping("/dispense-items/{itemId}/clarifications")
    public ResponseEntity<ApiResponse<Map<String, Object>>> propose(
            @PathVariable UUID itemId,
            @Valid @RequestBody ProposeRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ClarificationService.ProposalOutcome outcome = clarificationService.propose(
                itemId, request.targetDrugCode(), request.reason(), request.patientConsent());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("autoApplied", outcome.autoApplied());
        if (outcome.autoApplied()) {
            body.put("itemId", outcome.item().getItemId());
            body.put("drugCode", outcome.item().getDrugCode());
            body.put("status", outcome.item().getStatus().name());
        } else {
            body.put("clarification", view(outcome.clarification()));
        }

        log.info("Substitution proposal: itemId={}, target={}, autoApplied={}",
                itemId, request.targetDrugCode(), outcome.autoApplied());

        return ResponseEntity.status(outcome.autoApplied() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.ok(body, correlationId));
    }

    /** Prescriber approve/decline seam. Decline is fail-closed. */
    @PostMapping("/clarifications/{clarificationId}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(
            @PathVariable UUID clarificationId,
            @Valid @RequestBody ResolveRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        boolean approve = switch (request.decision().trim().toUpperCase(java.util.Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> true;
            case "DECLINE", "DECLINED" -> false;
            default -> throw new IllegalArgumentException(
                    "Decision must be APPROVE or DECLINE: " + request.decision());
        };

        ClarificationRequestEntity resolved =
                clarificationService.resolve(clarificationId, approve, request.note());

        log.info("Clarification resolved: id={}, decision={}", clarificationId, resolved.getStatus());

        return ResponseEntity.ok(ApiResponse.ok(view(resolved), correlationId));
    }

    /** Clarification worklist for a dispense order. */
    @GetMapping("/dispense-orders/{orderId}/clarifications")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forOrder(@PathVariable UUID orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> list = clarificationService.forOrder(orderId).stream()
                .map(ClarificationController::view).toList();
        return ResponseEntity.ok(ApiResponse.ok(list, correlationId));
    }

    @GetMapping("/clarifications/{clarificationId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID clarificationId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(view(clarificationService.get(clarificationId)), correlationId));
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> view(ClarificationRequestEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clarificationId", c.getClarificationId());
        m.put("dispenseOrderId", c.getDispenseOrderId());
        m.put("dispenseItemId", c.getDispenseItemId());
        m.put("sourceDrugCode", c.getSourceDrugCode());
        m.put("targetDrugCode", c.getTargetDrugCode());
        m.put("reason", c.getReason());
        m.put("patientConsent", c.isPatientConsent());
        m.put("status", c.getStatus().name());
        m.put("requestedBy", c.getRequestedBy());
        m.put("requestedAt", asString(c.getRequestedAt()));
        m.put("resolvedBy", c.getResolvedBy());
        m.put("resolvedAt", asString(c.getResolvedAt()));
        m.put("resolutionNote", c.getResolutionNote());
        m.put("appliedAt", asString(c.getAppliedAt()));
        return m;
    }

    private static String asString(OffsetDateTime t) {
        return t != null ? t.toString() : null;
    }
}
