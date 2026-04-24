package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.CreateVisibilityEscalationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.ReviewVisibilityEscalationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.VisibilityEscalationGrantResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.VisibilityEscalationRequestResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.VisibilityEscalationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow-gated visibility escalation (distinct from break-glass emergency access).
 */
@RestController
@RequestMapping("/v1/visibility-escalations")
public class VisibilityEscalationController {

    private final VisibilityEscalationService visibilityEscalationService;

    public VisibilityEscalationController(VisibilityEscalationService visibilityEscalationService) {
        this.visibilityEscalationService = visibilityEscalationService;
    }

    @PostMapping("/requests")
    public ResponseEntity<VisibilityEscalationRequestResponse> createRequest(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @Valid @RequestBody CreateVisibilityEscalationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(visibilityEscalationService.createRequest(tenantId, actorId, body));
    }

    @GetMapping("/requests/pending")
    public List<VisibilityEscalationRequestResponse> listPending(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId) {
        return visibilityEscalationService.listPending(tenantId, actorId);
    }

    @PostMapping("/requests/{id}/review")
    public ResponseEntity<VisibilityEscalationGrantResponse> review(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @PathVariable long id,
            @Valid @RequestBody ReviewVisibilityEscalationRequest body) {
        String reviewer = jwt != null ? jwt.getSubject() : "anonymous";
        List<String> roles = realmRoles(jwt);
        Optional<VisibilityEscalationGrantResponse> grant =
                visibilityEscalationService.reviewRequest(tenantId, id, reviewer, roles, body);
        return grant.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm == null || realm.get("roles") == null) {
            return List.of();
        }
        Object raw = realm.get("roles");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }
}
