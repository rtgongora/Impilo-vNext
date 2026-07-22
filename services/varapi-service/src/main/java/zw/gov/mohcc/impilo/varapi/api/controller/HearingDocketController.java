package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.HearingDocketService;
import zw.gov.mohcc.impilo.varapi.core.HearingDocketService.RecusalRequiredException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CaseDocketAssignmentEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Committee hearings + dockets (ROM-U4). A committee member sees only their docket; assigning a
 *  member to their own case is recusal-guarded (403 RECUSAL_REQUIRED). */
@RestController
@RequestMapping("/v1/internal/regulatory/hearings")
public class HearingDocketController {

    private final HearingDocketService service;

    public HearingDocketController(HearingDocketService service) { this.service = service; }

    /** The signed-in committee member's docket — the only cases they may see. */
    @GetMapping("/my-docket")
    public ResponseEntity<List<CaseDocketAssignmentEntity>> myDocket() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.myDocket(ctx.tenantId(), ctx.actorId()));
    }

    @PostMapping("/docket")
    public ResponseEntity<CaseDocketAssignmentEntity> assign(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.assignToDocket(
                    ctx.tenantId(), asLong(body.get("caseId")), str(body.get("memberHealthId")), str(body.get("committeeRef"))));
        } catch (RecusalRequiredException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RECUSAL_REQUIRED: " + e.getMessage());
        }
    }

    @PostMapping("/{caseId}/schedule")
    public ResponseEntity<Object> schedule(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        OffsetDateTime at = body.get("scheduledAt") != null ? OffsetDateTime.parse(body.get("scheduledAt").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.scheduleHearing(ctx.tenantId(), caseId, str(body.get("committeeRef")), at));
    }

    @PostMapping("/{caseId}/appeal")
    public ResponseEntity<Object> appeal(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.fileAppeal(
                ctx.tenantId(), caseId, str(body.get("appealedDecision")), str(body.get("grounds")), ctx.actorId()));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static Long asLong(Object o) { try { return o == null ? null : Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; } }
}
