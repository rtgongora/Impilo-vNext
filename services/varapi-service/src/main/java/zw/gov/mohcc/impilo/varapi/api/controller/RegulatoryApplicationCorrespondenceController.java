package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.RegulatoryApplicationCorrespondenceService;
import zw.gov.mohcc.impilo.varapi.core.RegulatoryApplicationCorrespondenceService.RecusalRequiredException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Two-way application correspondence (ROM-W4). Applicant lanes ({@code /applicant/**}) and regulator
 * lanes ({@code /regulator/**}); the regulator lanes are recusal-guarded (a self-application action
 * returns 403 RECUSAL_REQUIRED). The applicant thread never includes internal notes.
 */
@RestController
@RequestMapping("/v1/internal/regulatory/applications/{applicationId}/correspondence")
public class RegulatoryApplicationCorrespondenceController {

    private final RegulatoryApplicationCorrespondenceService service;

    public RegulatoryApplicationCorrespondenceController(RegulatoryApplicationCorrespondenceService service) {
        this.service = service;
    }

    // ── Applicant lanes ──────────────────────────────────────────────
    @GetMapping("/applicant")
    public ResponseEntity<Map<String, Object>> applicantView(@PathVariable Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "messages", service.applicantThread(ctx.tenantId(), applicationId),
                "informationRequests", service.rfis(ctx.tenantId(), applicationId)));
    }

    @PostMapping("/rfis/{rfiId}/respond")
    public ResponseEntity<Object> respondRfi(@PathVariable Long applicationId, @PathVariable Long rfiId,
                                             @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.respondRfi(ctx.tenantId(), ctx.actorId(), rfiId, str(body.get("responseNotes"))));
    }

    @PostMapping("/applicant/messages")
    public ResponseEntity<Object> applicantMessage(@PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.postMessage(
                ctx.tenantId(), ctx.actorId(), applicationId, "INBOUND", "APPLICANT", "APPLICANT", str(body.get("body"))));
    }

    // ── Regulator lanes (recusal-guarded) ────────────────────────────
    @GetMapping("/regulator")
    public ResponseEntity<Map<String, Object>> regulatorView(@PathVariable Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "messages", service.fullThread(ctx.tenantId(), applicationId),
                "informationRequests", service.rfis(ctx.tenantId(), applicationId)));
    }

    @PostMapping("/rfis")
    public ResponseEntity<Object> openRfi(@PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.openRfi(
                    ctx.tenantId(), ctx.actorId(), applicationId, str(body.get("message")), date(body.get("dueDate"))));
        } catch (RecusalRequiredException e) {
            throw recusal(e);
        }
    }

    @PostMapping("/rfis/{rfiId}/close")
    public ResponseEntity<Object> closeRfi(@PathVariable Long applicationId, @PathVariable Long rfiId) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            return ResponseEntity.ok(service.closeRfi(ctx.tenantId(), ctx.actorId(), rfiId));
        } catch (RecusalRequiredException e) {
            throw recusal(e);
        }
    }

    @PostMapping("/regulator/messages")
    public ResponseEntity<Object> regulatorMessage(@PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String visibility = str(body.get("visibility")); // APPLICANT or INTERNAL
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.postMessage(
                    ctx.tenantId(), ctx.actorId(), applicationId, "OUTBOUND",
                    visibility, str(body.get("capacity")), str(body.get("body"))));
        } catch (RecusalRequiredException e) {
            throw recusal(e);
        }
    }

    private static ResponseStatusException recusal(RecusalRequiredException e) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "RECUSAL_REQUIRED: " + e.getMessage());
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static LocalDate date(Object o) {
        try { return o == null ? null : LocalDate.parse(o.toString()); } catch (Exception e) { return null; }
    }
}
