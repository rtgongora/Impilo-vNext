package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.DisciplinaryProceedingService;
import zw.gov.mohcc.impilo.varapi.core.DisciplinaryProceedingService.RecusalRequiredException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;

import java.util.List;
import java.util.Map;

/**
 * Disciplinary proceeding lanes for the officer console (ROM-U3). Every write is recusal-guarded in
 * the service (an officer cannot run a proceeding against themselves) and returns 403
 * RECUSAL_REQUIRED. The rito firewall holds: a proceeding is only ever opened here by an officer.
 */
@RestController
@RequestMapping("/v1/internal/regulatory/disciplinary")
public class DisciplinaryProceedingController {

    private final DisciplinaryProceedingService service;

    public DisciplinaryProceedingController(DisciplinaryProceedingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProviderDisciplinaryCaseEntity> open(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.openFromReferral(
                    ctx.tenantId(), ctx.actorId(), asLong(body.get("providerId")), asLong(body.get("councilId")),
                    str(body.get("ritoCaseId")), str(body.get("summary"))));
        } catch (RecusalRequiredException e) { throw recusal(e); }
    }

    @PostMapping("/{caseId}/advance")
    public ResponseEntity<ProviderDisciplinaryCaseEntity> advance(@PathVariable Long caseId,
                                                                  @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            return ResponseEntity.ok(service.advance(ctx.tenantId(), ctx.actorId(), caseId, str(body.get("toStage"))));
        } catch (RecusalRequiredException e) { throw recusal(e); }
    }

    @PostMapping("/{caseId}/determine")
    public ResponseEntity<ProviderDisciplinaryCaseEntity> determine(@PathVariable Long caseId,
                                                                    @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            return ResponseEntity.ok(service.determine(ctx.tenantId(), ctx.actorId(), caseId,
                    str(body.get("decision")), str(body.get("restrictionType")), str(body.get("restrictionDescription"))));
        } catch (RecusalRequiredException e) { throw recusal(e); }
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderDisciplinaryCaseEntity>> forProvider(@PathVariable Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.forProvider(ctx.tenantId(), providerId));
    }

    private static ResponseStatusException recusal(RecusalRequiredException e) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "RECUSAL_REQUIRED: " + e.getMessage());
    }
    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static Long asLong(Object o) {
        if (o == null) { return null; }
        try { return Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
