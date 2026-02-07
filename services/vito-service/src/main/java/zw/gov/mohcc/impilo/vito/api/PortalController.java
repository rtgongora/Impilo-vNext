package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.config.StepUpRequired;
import zw.gov.mohcc.impilo.vito.core.*;
import zw.gov.mohcc.impilo.vito.core.issuance.IssuanceStateMachineService;
import zw.gov.mohcc.impilo.vito.core.pickup.DelegatedPickupService;
import zw.gov.mohcc.impilo.vito.core.qr.QrSigningService;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;

import java.util.*;

/**
 * Portal endpoints — internet-facing, designed for citizen self-service.
 *
 * SECURITY RULES:
 * - Responses are indistinguishable for existence checks (anti-enumeration)
 * - Step-up required for sensitive operations
 * - Rate limiting enforced by Envoy
 */
@RestController
@RequestMapping("/v1/portal")
public class PortalController {

    private final IssuanceStateMachineService issuanceService;
    private final IdentityService identityService;
    private final ImpiloIdAliasService aliasService;
    private final DelegatedPickupService pickupService;
    private final QrSigningService qrSigningService;
    private final HmacService hmacService;

    public PortalController(IssuanceStateMachineService issuanceService,
                             IdentityService identityService,
                             ImpiloIdAliasService aliasService,
                             DelegatedPickupService pickupService,
                             QrSigningService qrSigningService,
                             HmacService hmacService) {
        this.issuanceService = issuanceService;
        this.identityService = identityService;
        this.aliasService = aliasService;
        this.pickupService = pickupService;
        this.qrSigningService = qrSigningService;
        this.hmacService = hmacService;
    }

    /**
     * POST /v1/portal/id/request — request new or replacement ID.
     * Response is generic regardless of outcome (anti-enumeration).
     */
    @PostMapping("/id/request")
    public ResponseEntity<Map<String, Object>> requestId(@RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String type = body.getOrDefault("type", "NEW");
        String healthIdStr = body.get("healthId");

        try {
            UUID healthId;
            if (healthIdStr != null) {
                healthId = UUID.fromString(healthIdStr);
            } else {
                // For NEW requests, create a client first
                String givenName = body.getOrDefault("givenName", "");
                String familyName = body.getOrDefault("familyName", "");
                String dob = body.get("dateOfBirth");
                String sex = body.get("sex");
                IdentityService.ClientRegistrationResult result = identityService.register(
                        tenantId, givenName, familyName, dob, sex, ctx.actorId());
                healthId = result.client().getHealthId();
            }

            IssuanceRequestEntity req = issuanceService.submit(
                    tenantId, healthId,
                    IssuanceType.valueOf(type.toUpperCase()),
                    IssuanceChannel.PORTAL,
                    ctx.actorId());

            return ResponseEntity.accepted().body(Map.of(
                    "status", "ACCEPTED",
                    "message", "Your request has been submitted. You will be contacted for a proofing appointment.",
                    "referenceId", req.getId()
            ));
        } catch (Exception e) {
            // Generic response for anti-enumeration
            return ResponseEntity.accepted().body(Map.of(
                    "status", "ACCEPTED",
                    "message", "Your request has been submitted. You will be contacted for a proofing appointment."
            ));
        }
    }

    /**
     * POST /v1/portal/id/recovery/start — initiate ID recovery.
     * Always returns generic response.
     */
    @StepUpRequired(reason = "ID recovery requires step-up authentication")
    @PostMapping("/id/recovery/start")
    public ResponseEntity<Map<String, String>> recoveryStart(@RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String aliasValue = body.get("impiloId");

        // Attempt to resolve (result doesn't leak to response)
        if (aliasValue != null) {
            Optional<UUID> resolved = aliasService.resolve(tenantId, "IMPILO_ID", aliasValue);
            if (resolved.isPresent()) {
                try {
                    issuanceService.submit(tenantId, resolved.get(), IssuanceType.RECOVERY,
                            IssuanceChannel.PORTAL, ctx.actorId());
                } catch (Exception ignored) {
                    // Swallow — generic response
                }
            }
        }

        // Always same response (indistinguishable)
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "If your ID exists, a recovery request has been initiated. You will be contacted."
        ));
    }

    /**
     * POST /v1/portal/id/recovery/verify — verify recovery with proof.
     */
    @StepUpRequired(reason = "Recovery verification requires step-up")
    @PostMapping("/id/recovery/verify")
    public ResponseEntity<Map<String, String>> recoveryVerify(@RequestBody Map<String, String> body) {
        // In production: verify proofing artifacts, biometric binding, etc.
        return ResponseEntity.ok(Map.of(
                "status", "VERIFIED",
                "message", "Recovery verification submitted. An operator will review your case."
        ));
    }

    /**
     * GET /v1/portal/me — minimal self-service info.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        // Resolve actor to client
        Optional<UUID> healthId = aliasService.resolve(tenantId, "IMPILO_ID", actorId);
        if (healthId.isEmpty()) {
            return ResponseEntity.ok(Map.of("registered", false));
        }

        return identityService.findByHealthId(tenantId, healthId.get())
                .map(client -> ResponseEntity.ok(Map.<String, Object>of(
                        "registered", true,
                        "healthId", client.getHealthId().toString(),
                        "status", client.getStatus().name(),
                        "impiloId", client.getImpiloId() != null ? client.getImpiloId() : ""
                )))
                .orElse(ResponseEntity.ok(Map.of("registered", false)));
    }

    /**
     * GET /v1/portal/health-id/qr — signed QR for wallet/presentation.
     * No PII in the QR — only an opaque pointer.
     */
    @GetMapping("/health-id/qr")
    public ResponseEntity<Map<String, String>> healthIdQr() {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        Optional<UUID> healthId = aliasService.resolve(tenantId, "IMPILO_ID", actorId);
        if (healthId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Pointer is HMAC of health_id (not plaintext)
        String pointer = hmacService.computeLookupHash(healthId.get().toString());
        String qr = qrSigningService.sign(tenantId.toString(), "HEALTH_ID", pointer, 3600);

        return ResponseEntity.ok(Map.of("qr", qr));
    }

    /**
     * POST /v1/portal/delegated-pickup/create — create pickup delegation (step-up required).
     */
    @StepUpRequired(reason = "Creating a delegated pickup requires step-up authentication")
    @PostMapping("/delegated-pickup/create")
    public ResponseEntity<Map<String, Object>> createPickup(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        Long issuanceRequestId = ((Number) body.get("issuanceRequestId")).longValue();
        String delegateName = (String) body.get("delegateName");
        String delegateContact = body.containsKey("delegateContact") ? body.get("delegateContact").toString() : "{}";
        String delegateIdRef = (String) body.get("delegateIdRef");
        UUID facilityId = UUID.fromString((String) body.get("facilityId"));
        int expiryHours = body.containsKey("expiryHours") ? ((Number) body.get("expiryHours")).intValue() : 72;

        DelegatedPickupService.PickupPackage pkg = pickupService.create(
                tenantId, issuanceRequestId, delegateName, delegateContact,
                delegateIdRef, facilityId, expiryHours);

        return ResponseEntity.ok(Map.of(
                "pickupId", pkg.pickupId(),
                "pickupToken", pkg.pickupToken(),
                "otp", pkg.otp(),
                "expiresAt", pkg.expiresAt().toString(),
                "message", "Give the OTP to the delegate. They must present it with this QR at the facility."
        ));
    }

    /**
     * POST /v1/portal/delegated-pickup/redeem — facility redeems a pickup.
     */
    @PostMapping("/delegated-pickup/redeem")
    public ResponseEntity<Map<String, String>> redeemPickup(@RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        String pickupToken = body.get("pickupToken");
        String otp = body.get("otp");
        String redeemedBy = ctx.actorId();

        try {
            pickupService.redeem(pickupToken, otp, redeemedBy);
            return ResponseEntity.ok(Map.of(
                    "status", "REDEEMED",
                    "message", "Pickup verified. Proceed with card delivery."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }
}
