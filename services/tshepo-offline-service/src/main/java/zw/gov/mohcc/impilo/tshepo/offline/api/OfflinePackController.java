package zw.gov.mohcc.impilo.tshepo.offline.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflinePackRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflinePackResponse;
import zw.gov.mohcc.impilo.tshepo.offline.core.OfflinePackService;

import java.util.UUID;

/**
 * REST controller for offline pack operations.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *     <li>POST /v1/offline/packs — generate a new offline pack for a facility</li>
 *     <li>GET /v1/offline/packs/{id} — download a specific offline pack</li>
 *     <li>GET /v1/offline/packs/facility/{facilityId} — get the latest pack for a facility</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/offline/packs")
public class OfflinePackController {

    private static final Logger log = LoggerFactory.getLogger(OfflinePackController.class);

    private final OfflinePackService packService;

    public OfflinePackController(OfflinePackService packService) {
        this.packService = packService;
    }

    /**
     * Generate a new offline pack for a facility.
     * Snapshots policy rules, consent directives, and terminology codes.
     */
    @PostMapping
    public ResponseEntity<OfflinePackResponse> generatePack(
            @Valid @RequestBody OfflinePackRequest request,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(TrustHeaders.ACTOR_ID) String actorId,
            @RequestHeader(TrustHeaders.CORRELATION_ID) UUID correlationId) {

        log.info("POST /v1/offline/packs — tenant={}, facility={}, actor={}",
                tenantId, request.facilityId(), actorId);

        OfflinePackResponse response = packService.generatePack(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Download a specific offline pack by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OfflinePackResponse> getPack(
            @PathVariable UUID id,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId) {

        log.debug("GET /v1/offline/packs/{} — tenant={}", id, tenantId);

        OfflinePackResponse response = packService.getPack(id, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get the latest offline pack for a facility.
     */
    @GetMapping("/facility/{facilityId}")
    public ResponseEntity<OfflinePackResponse> getLatestPackForFacility(
            @PathVariable UUID facilityId,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId) {

        log.debug("GET /v1/offline/packs/facility/{} — tenant={}", facilityId, tenantId);

        OfflinePackResponse response = packService.getLatestPackForFacility(facilityId, tenantId);
        return ResponseEntity.ok(response);
    }
}
