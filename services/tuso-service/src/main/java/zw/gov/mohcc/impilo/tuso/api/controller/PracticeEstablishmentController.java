package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.PracticeEstablishmentService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PracticeEstablishmentCaseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pre-licensing practice establishment (ROM-U6). A prospective owner builds a practice profile
 * before any facility exists; the canonical facility is created only on APPROVAL. Applicant lanes
 * key on the person's Health-ID from the trust context (their OWN cases).
 */
@RestController
@RequestMapping("/v1/internal/regulatory/practice-establishments")
public class PracticeEstablishmentController {

    private final PracticeEstablishmentService service;

    public PracticeEstablishmentController(PracticeEstablishmentService service) { this.service = service; }

    @GetMapping("/mine")
    public ResponseEntity<List<PracticeEstablishmentCaseEntity>> mine() {
        TrustContext ctx = TrustContextHolder.require();
        UUID applicant = parse(ctx.actorId());
        return ResponseEntity.ok(applicant == null ? List.of() : service.forApplicant(ctx.tenantId(), applicant));
    }

    @PostMapping
    public ResponseEntity<PracticeEstablishmentCaseEntity> createDraft(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID applicant = parse(ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDraft(
                ctx.tenantId(), applicant, str(body.get("proposedName")), str(body.get("categoryCode")),
                str(body.get("proposedProvince"))));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<PracticeEstablishmentCaseEntity> submit(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.submit(ctx.tenantId(), id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PracticeEstablishmentCaseEntity> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.approve(ctx.tenantId(), id, ctx.actorId(), str(body.get("decisionNotes"))));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PracticeEstablishmentCaseEntity> reject(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.reject(ctx.tenantId(), id, ctx.actorId(), str(body.get("decisionNotes"))));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static UUID parse(String s) { try { return s == null ? null : UUID.fromString(s.trim()); } catch (Exception e) { return null; } }
}
