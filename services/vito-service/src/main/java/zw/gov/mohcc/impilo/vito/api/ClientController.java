package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.shared.visibility.AggregateVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.vito.core.IdentityService;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;

import java.util.UUID;

/**
 * Client Registry REST API — dual-mode.
 * Both INTERNAL and EXTERNAL consumers can read clients.
 * Only INTERNAL consumers can create/modify clients.
 */
@RestController
@RequestMapping("/v1/clients")
public class ClientController {

    private final IdentityService identityService;

    public ClientController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ClientEntity>>> listClients(
            @RequestParam(required = false) IdentityStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.ok(ApiResponse.ok(
                    PagedResponse.of(java.util.List.of(), page, size, 0),
                    ctx.correlationId().toString()));
        }
        Page<ClientEntity> clients = identityService.listClients(
                ctx.tenantId(), status, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(clients.getContent(), page, size, clients.getTotalElements()),
                ctx.correlationId().toString()));
    }

    @GetMapping("/{healthId}")
    public ResponseEntity<ApiResponse<ClientEntity>> getClient(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("VISIBILITY_AGGREGATE_ONLY",
                            "Client identity detail is not available under aggregate-only visibility.",
                            HttpStatus.FORBIDDEN.value(),
                            ctx.correlationId().toString()));
        }

        return identityService.findByHealthId(ctx.tenantId(), healthId)
                .map(client -> ResponseEntity.ok(
                        ApiResponse.ok(client, ctx.correlationId().toString())))
                .orElse(ResponseEntity.status(404).body(
                        ApiResponse.error("NOT_FOUND", "Client not found", 404,
                                ctx.correlationId().toString())));
    }

    @GetMapping("/by-impilo-id/{impiloId}")
    public ResponseEntity<ApiResponse<ClientEntity>> getClientByImpiloId(@PathVariable String impiloId) {
        TrustContext ctx = TrustContextHolder.require();
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.error("VISIBILITY_AGGREGATE_ONLY",
                            "Client identity detail is not available under aggregate-only visibility.",
                            HttpStatus.FORBIDDEN.value(),
                            ctx.correlationId().toString()));
        }

        return identityService.findByImpiloId(ctx.tenantId(), impiloId)
                .map(client -> ResponseEntity.ok(
                        ApiResponse.ok(client, ctx.correlationId().toString())))
                .orElse(ResponseEntity.status(404).body(
                        ApiResponse.error("NOT_FOUND", "Client not found", 404,
                                ctx.correlationId().toString())));
    }

    @PostMapping("/{healthId}/verify")
    public ResponseEntity<ApiResponse<ClientEntity>> verifyClient(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity verified = identityService.verify(ctx.tenantId(), healthId, ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(verified, ctx.correlationId().toString()));
    }

    @PostMapping("/{healthId}/deactivate")
    public ResponseEntity<ApiResponse<ClientEntity>> deactivateClient(
            @PathVariable UUID healthId,
            @RequestParam(required = false) String reason) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity client = identityService.deactivate(ctx.tenantId(), healthId, reason);
        return ResponseEntity.ok(ApiResponse.ok(client, ctx.correlationId().toString()));
    }

    @PostMapping("/{healthId}/deceased")
    public ResponseEntity<ApiResponse<ClientEntity>> markDeceased(
            @PathVariable UUID healthId,
            @RequestParam String deathNotificationRef) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity client = identityService.markDeceased(ctx.tenantId(), healthId, deathNotificationRef);
        return ResponseEntity.ok(ApiResponse.ok(client, ctx.correlationId().toString()));
    }
}
