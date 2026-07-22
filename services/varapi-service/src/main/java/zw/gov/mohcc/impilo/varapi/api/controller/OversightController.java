package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.OversightService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.OversightEscalationGrantEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HPA oversight grants (ROM-U5). Aggregate indicators come from reporting; row-level council
 *  case access is granted per-case only — this manages those grants. */
@RestController
@RequestMapping("/v1/internal/regulatory/oversight")
public class OversightController {

    private final OversightService service;

    public OversightController(OversightService service) { this.service = service; }

    @GetMapping("/grants")
    public ResponseEntity<List<OversightEscalationGrantEntity>> grants(@RequestParam("orgId") UUID orgId) {
        return ResponseEntity.ok(service.grantsFor(orgId));
    }

    @PostMapping("/grants")
    public ResponseEntity<OversightEscalationGrantEntity> grant(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.grant(
                ctx.tenantId(), asLong(body.get("councilId")), str(body.get("subjectType")), str(body.get("subjectRef")),
                UUID.fromString(str(body.get("grantedToOrgId"))), str(body.get("reason")), ctx.actorId()));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static Long asLong(Object o) { try { return o == null ? null : Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; } }
}
