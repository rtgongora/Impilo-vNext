package zw.gov.mohcc.impilo.offlineedge.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.EntitlementVerificationResult;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlement;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlementVerifier;
import zw.gov.mohcc.impilo.offlineedge.api.dto.SyncRequest;
import zw.gov.mohcc.impilo.offlineedge.api.dto.SyncResponse;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineSyncService;

import java.util.UUID;

/**
 * REST endpoint for syncing offline-captured actions.
 *
 * <p>Accepts a batch of actions from an edge device and processes them:
 * stores locally, emits outbox events with {@code EventEnvelope} format,
 * and triggers replay for eligible actions.</p>
 */
@RestController
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);
    private static final String ENTITLEMENT_HEADER = "X-Offline-Entitlement";

    private final OfflineEntitlementVerifier entitlementVerifier;
    private final OfflineSyncService syncService;

    public SyncController(OfflineEntitlementVerifier entitlementVerifier,
                          OfflineSyncService syncService) {
        this.entitlementVerifier = entitlementVerifier;
        this.syncService = syncService;
    }

    /**
     * Push queued offline actions for synchronization.
     *
     * <p>Each action in the batch is validated, stored with audit, and an outbox
     * event is written with the original correlation_id preserved.</p>
     */
    @PostMapping("/internal/v1/offline/sync")
    public ResponseEntity<?> syncActions(
            @RequestHeader(value = ENTITLEMENT_HEADER, required = false) String entitlementToken,
            @Valid @RequestBody SyncRequest request) {

        if (entitlementToken == null || entitlementToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorEnvelope.of("MISSING_ENTITLEMENT",
                            "X-Offline-Entitlement header is required",
                            UUID.randomUUID().toString(), null));
        }

        EntitlementVerificationResult verification = entitlementVerifier.verify(entitlementToken);
        if (!verification.valid()) {
            log.warn("Sync entitlement denied: {}", verification.reason());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorEnvelope.of("ENTITLEMENT_DENIED",
                            verification.reason(),
                            UUID.randomUUID().toString(), null));
        }

        OfflineEntitlement entitlement = verification.entitlement();

        // Verify device fingerprint binding
        if (entitlement.deviceFingerprint() != null && !entitlement.deviceFingerprint().isBlank()) {
            String requestDevice = request.deviceId();
            if (requestDevice == null || !entitlement.deviceFingerprint().equals(requestDevice)) {
                log.warn("Sync device fingerprint mismatch: entitlement={}, request={}",
                        entitlement.deviceFingerprint(), requestDevice);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ErrorEnvelope.of("DEVICE_MISMATCH",
                                "Device fingerprint does not match entitlement binding",
                                UUID.randomUUID().toString(), null));
            }
        }

        try {
            SyncResponse response = syncService.processSyncBatch(entitlement, request);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            log.error("Sync batch processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorEnvelope.of("SYNC_FAILED", e.getMessage(),
                            UUID.randomUUID().toString(), null));
        }
    }
}
