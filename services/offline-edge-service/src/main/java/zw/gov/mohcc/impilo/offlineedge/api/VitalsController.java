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
import zw.gov.mohcc.impilo.offlineedge.api.dto.CaptureVitalsRequest;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineVitalsService;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineVitalsService.MaxEncountersExceededException;

import java.util.*;

/**
 * REST endpoint for offline vitals capture.
 *
 * <p>Requires an {@code X-Offline-Entitlement} header containing a JWS-signed
 * capability token issued by TSHEPO's offline service. The token must grant the
 * {@code CAPTURE_VITALS} capability.</p>
 */
@RestController
public class VitalsController {

    private static final Logger log = LoggerFactory.getLogger(VitalsController.class);
    private static final String ENTITLEMENT_HEADER = "X-Offline-Entitlement";
    private static final String REQUIRED_CAPABILITY = "CAPTURE_VITALS";

    private final OfflineEntitlementVerifier entitlementVerifier;
    private final OfflineVitalsService vitalsService;

    public VitalsController(OfflineEntitlementVerifier entitlementVerifier,
                            OfflineVitalsService vitalsService) {
        this.entitlementVerifier = entitlementVerifier;
        this.vitalsService = vitalsService;
    }

    /**
     * Capture a vital sign reading offline.
     *
     * <p>Entitlement is verified from the JWT in the {@code X-Offline-Entitlement} header.
     * On success, the vital is stored locally with audit trail and queued for replay.</p>
     */
    @PostMapping("/internal/v1/offline/vitals")
    public ResponseEntity<?> captureVitals(
            @RequestHeader(value = ENTITLEMENT_HEADER, required = false) String entitlementToken,
            @Valid @RequestBody CaptureVitalsRequest request) {

        // Step 1: Verify entitlement JWT
        if (entitlementToken == null || entitlementToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorEnvelope.of("MISSING_ENTITLEMENT",
                            "X-Offline-Entitlement header is required",
                            UUID.randomUUID().toString(), null));
        }

        EntitlementVerificationResult verification = entitlementVerifier.verify(
                entitlementToken, REQUIRED_CAPABILITY);

        if (!verification.valid()) {
            log.warn("Offline entitlement denied: {}", verification.reason());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorEnvelope.of("ENTITLEMENT_DENIED",
                            verification.reason(),
                            UUID.randomUUID().toString(), null));
        }

        OfflineEntitlement entitlement = verification.entitlement();

        // Step 2: Verify device fingerprint binding
        if (entitlement.deviceFingerprint() != null && !entitlement.deviceFingerprint().isBlank()) {
            if (request.deviceId() == null || !entitlement.deviceFingerprint().equals(request.deviceId())) {
                log.warn("Device fingerprint mismatch: entitlement={}, request={}",
                        entitlement.deviceFingerprint(), request.deviceId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ErrorEnvelope.of("DEVICE_MISMATCH",
                                "Device fingerprint does not match entitlement binding",
                                UUID.randomUUID().toString(), null));
            }
        }

        // Step 3: Capture the vital sign
        try {
            Map<String, Object> result = vitalsService.captureVital(entitlement, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (OfflineVitalsService.MaxEncountersExceededException e) {
            log.warn("Max offline encounters exceeded: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorEnvelope.of("MAX_ENCOUNTERS_EXCEEDED", e.getMessage(),
                            UUID.randomUUID().toString(), null));
        } catch (Exception e) {
            log.error("Failed to capture offline vital: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorEnvelope.of("CAPTURE_FAILED", e.getMessage(),
                            UUID.randomUUID().toString(), null));
        }
    }
}
