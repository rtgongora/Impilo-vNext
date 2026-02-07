package zw.gov.mohcc.impilo.tshepo.offline.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflineActionRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflineActionResponse;
import zw.gov.mohcc.impilo.tshepo.offline.core.OfflineActionService;

import java.util.UUID;

/**
 * REST controller for logging and querying offline actions.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *     <li>POST /v1/offline/actions — log an offline action</li>
 *     <li>GET /v1/offline/actions — query logged offline actions (paginated)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/offline/actions")
public class OfflineActionController {

    private static final Logger log = LoggerFactory.getLogger(OfflineActionController.class);

    private final OfflineActionService actionService;

    public OfflineActionController(OfflineActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * Log an offline action.
     * Validates the action against the capability token and rules engine,
     * issues O-CPID if required, and persists the action log.
     */
    @PostMapping
    public ResponseEntity<OfflineActionResponse> logAction(
            @Valid @RequestBody OfflineActionRequest request,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(TrustHeaders.ACTOR_ID) String actorId,
            @RequestHeader(TrustHeaders.CORRELATION_ID) UUID correlationId) {

        log.info("POST /v1/offline/actions — tenant={}, actor={}, action={}, tokenId={}",
                tenantId, actorId, request.actionType(), request.capabilityTokenId());

        OfflineActionResponse response = actionService.logAction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Query offline actions by tenant and actor (paginated).
     */
    @GetMapping
    public ResponseEntity<Page<OfflineActionResponse>> queryActions(
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(TrustHeaders.ACTOR_ID) String actorId,
            @PageableDefault(size = 50, sort = "performedAt") Pageable pageable) {

        log.debug("GET /v1/offline/actions — tenant={}, actor={}", tenantId, actorId);

        Page<OfflineActionResponse> responses = actionService.queryActions(tenantId, actorId, pageable);
        return ResponseEntity.ok(responses);
    }
}
